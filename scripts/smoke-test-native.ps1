<#
.SYNOPSIS
    Smoke test of the native Docker images skoda-mcp and skoda-api against a stub MySkoda Public API.

.DESCRIPTION
    1. Starts a stub upstream (python:3-alpine running scripts/smoke/upstream_stub.py with the test
       fixture src/test/resources/upstream/vehicle-full.json) on a private Docker network.
    2. skoda-mcp: `docker run -i`, speaks MCP over stdin/stdout: initialize, notifications/initialized,
       tools/list (5 tools with output schemas), tools/call get_vehicle_location and get_api_key_status.
       Every stdout line must be a JSON-RPC 2.0 message (stdout belongs to the protocol). Closing stdin
       must make the server exit with code 0 (the container is then removed by --rm).
    3. skoda-api: health on the management port 8081, 200 without credentials against the stub,
       /skoda-service-api.yaml and /swagger-ui.html.
    Prints startup times, memory and image sizes. Exit code 0 = all checks passed.

    Works with Windows PowerShell 5.1 and PowerShell 7 (Linux/macOS/Windows); needs Docker only.

.EXAMPLE
    ./scripts/smoke-test-native.ps1 -Build
    ./scripts/smoke-test-native.ps1 -McpImage skoda-mcp:native -ApiImage skoda-api:native
#>
[CmdletBinding()]
param(
    [string] $McpImage = 'skoda-mcp:native',
    [string] $ApiImage = 'skoda-api:native',
    # Build both images from the repository's Dockerfile first.
    [switch] $Build,
    [switch] $SkipMcp,
    [switch] $SkipApi,
    [int] $ApiPort = 18080,
    [int] $ManagementPort = 18081,
    [int] $TimeoutSeconds = 60
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$repo = Split-Path -Parent $PSScriptRoot
$network = 'skoda-smoke'
$upstreamName = 'skoda-smoke-upstream'
$mcpName = 'skoda-smoke-mcp'
$apiName = 'skoda-smoke-api'
$vin = 'TMBJJ7NE8L0123456'
$apiKey = 'smoke-dummy-api-key'
$upstreamUrl = "http://${upstreamName}:8080"

$script:checks = New-Object System.Collections.Generic.List[object]
$script:facts = [ordered]@{}

function Check([string] $name, [bool] $ok, [string] $detail = '') {
    $script:checks.Add([pscustomobject]@{ Result = $(if ($ok) { 'PASS' } else { 'FAIL' }); Check = $name; Detail = $detail })
    $color = $(if ($ok) { 'Green' } else { 'Red' })
    Write-Host ("[{0}] {1} {2}" -f $(if ($ok) { 'PASS' } else { 'FAIL' }), $name, $detail) -ForegroundColor $color
}

function Invoke-Docker {
    # Runs docker, returns stdout lines; throws on a non-zero exit code unless -AllowFailure.
    param([string[]] $Arguments, [switch] $AllowFailure)
    # Windows PowerShell turns native stderr lines into (terminating, with 'Stop') error records.
    $ErrorActionPreference = 'Continue'
    $output = & docker @Arguments 2>&1 | ForEach-Object { "$_" }
    if ($LASTEXITCODE -ne 0 -and -not $AllowFailure) {
        throw "docker $($Arguments -join ' ') failed ($LASTEXITCODE): $($output -join "`n")"
    }
    return $output
}

function Remove-SmokeContainers {
    foreach ($name in @($mcpName, $apiName, $upstreamName)) {
        Invoke-Docker -Arguments @('rm', '-f', $name) -AllowFailure | Out-Null
    }
    Invoke-Docker -Arguments @('network', 'rm', $network) -AllowFailure | Out-Null
}

function Get-ImageSizeMB([string] $image) {
    $bytes = [double](Invoke-Docker -Arguments @('image', 'inspect', '--format', '{{.Size}}', $image) | Select-Object -Last 1)
    return [math]::Round($bytes / 1MB, 1)
}

function Get-StartedLine([string] $text) {
    $m = [regex]::Match($text, 'Started SkodaApplication in ([0-9.]+) seconds[^\r\n]*')
    if ($m.Success) { return $m.Groups[1].Value }
    return $null
}

function Get-UpstreamRequests {
    return @(Invoke-Docker -Arguments @('logs', $upstreamName) | Where-Object { $_ -like 'REQUEST *' })
}

# ------------------------------------------------------------------------------------------------
# MCP over stdio
# ------------------------------------------------------------------------------------------------
$utf8 = New-Object System.Text.UTF8Encoding($false)

function Start-StdioProcess([string] $fileName, [string[]] $arguments) {
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $fileName
    $psi.Arguments = ($arguments -join ' ')
    $psi.UseShellExecute = $false
    $psi.RedirectStandardInput = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.StandardOutputEncoding = $utf8
    $psi.StandardErrorEncoding = $utf8
    # The stdin writer must not start with a UTF-8 BOM (the server would reject the first message).
    # .NET (PowerShell 7) has StandardInputEncoding; .NET Framework uses the console input encoding.
    $savedInputEncoding = $null
    if ($psi.PSObject.Properties['StandardInputEncoding']) {
        $psi.StandardInputEncoding = $utf8
    }
    else {
        $savedInputEncoding = [Console]::InputEncoding
        [Console]::InputEncoding = $utf8
    }
    try { $process = [System.Diagnostics.Process]::Start($psi) }
    finally { if ($null -ne $savedInputEncoding) { [Console]::InputEncoding = $savedInputEncoding } }
    return [pscustomobject]@{
        Process     = $process
        StderrTask  = $process.StandardError.ReadToEndAsync()
        PendingRead = $null
        Lines       = New-Object System.Collections.Generic.List[string]
    }
}

function Send-JsonRpc($session, [string] $json) {
    # Raw bytes on the base stream: no BOM, LF line ending, whatever the console encoding is.
    $bytes = $utf8.GetBytes($json + "`n")
    $session.Process.StandardInput.BaseStream.Write($bytes, 0, $bytes.Length)
    $session.Process.StandardInput.BaseStream.Flush()
}

function Receive-JsonRpc($session, [int] $id) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ($true) {
        if ($null -eq $session.PendingRead) { $session.PendingRead = $session.Process.StandardOutput.ReadLineAsync() }
        $remaining = [int]($deadline - (Get-Date)).TotalMilliseconds
        if ($remaining -le 0 -or -not $session.PendingRead.Wait($remaining)) {
            throw "No JSON-RPC response with id $id within $TimeoutSeconds s"
        }
        $line = $session.PendingRead.Result
        $session.PendingRead = $null
        if ($null -eq $line) { throw "Server closed stdout while waiting for response $id" }
        $session.Lines.Add($line)
        $message = $line | ConvertFrom-Json
        if ($message.PSObject.Properties['id'] -and $message.id -eq $id) { return $message }
    }
}

function Test-McpImage {
    Write-Host "`n== $McpImage (stdio MCP) ==" -ForegroundColor Cyan
    $dockerArgs = @('run', '-i', '--rm', '--name', $mcpName, '--network', $network,
        '-e', "SKODA_VIN=$vin", '-e', "SKODA_API_KEY=$apiKey", '-e', "SKODA_PUBLIC_API_URL=$upstreamUrl", $McpImage)
    $clock = [System.Diagnostics.Stopwatch]::StartNew()
    $session = Start-StdioProcess 'docker' $dockerArgs
    try {
        Send-JsonRpc $session '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"smoke-test","version":"1.0"}}}'
        $init = Receive-JsonRpc $session 1
        $script:facts['mcp: docker run -> initialize response'] = "$($clock.ElapsedMilliseconds) ms"
        Check 'mcp initialize' ($init.result.serverInfo.name -eq 'skoda-mcp') "server=$($init.result.serverInfo.name) $($init.result.serverInfo.version), protocol=$($init.result.protocolVersion)"
        Check 'mcp instructions in UTF-8' ([string]$init.result.instructions).Contains([string][char]0x160 + 'koda') ''

        Send-JsonRpc $session '{"jsonrpc":"2.0","method":"notifications/initialized"}'
        Send-JsonRpc $session '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
        $tools = @((Receive-JsonRpc $session 2).result.tools)
        $expected = @('get_api_key_status', 'get_vehicle_location', 'get_vehicle_overview', 'get_vehicle_range', 'get_vehicle_status')
        $names = @($tools | ForEach-Object { $_.name } | Sort-Object)
        Check 'mcp tools/list: 5 tools' (($names -join ',') -eq ($expected -join ',')) ($names -join ', ')
        $schemaOk = $true
        foreach ($tool in $tools) {
            if (-not $tool.PSObject.Properties['outputSchema'] -or $tool.outputSchema.type -ne 'object' -or
                -not $tool.annotations.readOnlyHint -or [string]::IsNullOrWhiteSpace($tool.description)) { $schemaOk = $false }
        }
        Check 'mcp tools: description, readOnlyHint, object output schema' $schemaOk ''
        $location = $tools | Where-Object { $_.name -eq 'get_vehicle_location' }
        $latitude = $location.outputSchema.properties.data.properties.latitude
        # Native check that the @Schema annotations of the generated DTOs were visible: nullable types + descriptions
        Check 'mcp output schema keeps @Schema metadata' ($null -ne $latitude -and ($latitude.type -contains 'null') -and
            -not [string]::IsNullOrWhiteSpace($latitude.description)) ($latitude | ConvertTo-Json -Compress -Depth 5)

        Send-JsonRpc $session '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"get_vehicle_location","arguments":{}}}'
        $call = (Receive-JsonRpc $session 3).result
        $isError = $call.PSObject.Properties['isError'] -and $call.isError
        $data = $call.structuredContent.data
        Check 'mcp tools/call get_vehicle_location' ((-not $isError) -and $data.state -eq 'PARKED' -and
            $data.latitude -eq 44.4361414 -and $data.longitude -eq 26.1027202 -and $call.structuredContent.freshness.stale -eq $false) `
            ($call.structuredContent | ConvertTo-Json -Compress -Depth 6)

        Send-JsonRpc $session '{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"get_api_key_status","arguments":{}}}'
        $key = (Receive-JsonRpc $session 4).result.structuredContent
        Check 'mcp tools/call get_api_key_status' ($key.keyExpiresAt -eq '2026-12-31T00:00:00Z' -and $key.rateLimitRemaining -eq 19) `
            ($key | ConvertTo-Json -Compress)

        $mem = Invoke-Docker -Arguments @('stats', '--no-stream', '--format', '{{.MemUsage}}', $mcpName) -AllowFailure | Select-Object -Last 1
        $script:facts['mcp: memory after 2 tool calls'] = "$mem"
    }
    finally {
        # MCP clients stop a stdio server by closing its stdin: the server must exit by itself with
        # code 0, and `docker run --rm` then removes the container. Force-stop only as a fallback.
        $clock.Restart()
        try { $session.Process.StandardInput.BaseStream.Close() } catch { }
        $exitedOnEof = $session.Process.WaitForExit(15000)
        if ($exitedOnEof) {
            $session.Process.WaitForExit() # flush the async stdout/stderr readers
            $script:facts['mcp: stdin closed -> exit'] = "$($clock.ElapsedMilliseconds) ms"
            $left = @(Invoke-Docker -Arguments @('ps', '-a', '-q', '--filter', "name=^/$mcpName$") -AllowFailure | Where-Object { $_ })
            Check 'mcp: exits with code 0 when stdin is closed' ($session.Process.ExitCode -eq 0 -and $left.Count -eq 0) `
                "exit=$($session.Process.ExitCode), containers left=$($left.Count)"
        }
        else {
            Check 'mcp: exits with code 0 when stdin is closed' $false 'still running 15 s after stdin was closed'
            Invoke-Docker -Arguments @('stop', '-t', '5', $mcpName) -AllowFailure | Out-Null
            $session.Process.WaitForExit(15000) | Out-Null
        }
        if ($null -ne $session.PendingRead) {
            if ($session.PendingRead.Wait(5000) -and $null -ne $session.PendingRead.Result) { $session.Lines.Add($session.PendingRead.Result) }
        }
        while ($null -ne ($rest = $session.Process.StandardOutput.ReadLine())) { $session.Lines.Add($rest) }
    }
    $stderr = $session.StderrTask.Result

    $impure = @($session.Lines | Where-Object {
            $line = $_
            try { ($line | ConvertFrom-Json).jsonrpc -ne '2.0' } catch { $true }
        })
    Check 'mcp stdout is pure JSON-RPC' ($impure.Count -eq 0) "$($session.Lines.Count) lines, $($impure.Count) not JSON-RPC $($impure | Select-Object -First 2)"
    $started = Get-StartedLine $stderr
    Check 'mcp logs on stderr (Started SkodaApplication)' ($null -ne $started) ''
    $script:facts['mcp: Spring startup (log)'] = "$started s"
    $requests = @(Get-UpstreamRequests)
    Check 'mcp: one upstream call with the API key' ($requests.Count -eq 1 -and $requests[0] -like "REQUEST /api/v1/vehicles/$vin*api-key=$apiKey") ($requests -join '; ')

    # Fail fast on invalid configuration (Bean Validation of SkodaProperties in the native image),
    # reported on stderr only.
    $session = Start-StdioProcess 'docker' @('run', '-i', '--rm', '-e', 'SKODA_VIN=not-a-vin', $McpImage)
    $session.Process.StandardInput.BaseStream.Close()
    $exited = $session.Process.WaitForExit($TimeoutSeconds * 1000)
    $stdout = $session.Process.StandardOutput.ReadToEnd()
    $stderr = $session.StderrTask.Result
    Check 'mcp: invalid config fails fast on stderr' ($exited -and $session.Process.ExitCode -ne 0 -and
        $stderr -match 'skoda\.vin' -and $stderr -match 'SKODA_API_KEY' -and $stdout.Trim().Length -eq 0) `
        "exit=$(if ($exited) { $session.Process.ExitCode } else { 'timeout' }), stdout bytes=$($stdout.Length)"
}

# ------------------------------------------------------------------------------------------------
# REST API
# ------------------------------------------------------------------------------------------------
Add-Type -AssemblyName System.Net.Http
$http = New-Object System.Net.Http.HttpClient
$http.Timeout = [TimeSpan]::FromSeconds(30)

function Invoke-HttpGet([string] $url) {
    $request = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::Get, $url)
    $response = $http.SendAsync($request).GetAwaiter().GetResult()
    $contentType = ''
    if ($null -ne $response.Content.Headers.ContentType) { $contentType = $response.Content.Headers.ContentType.ToString() }
    return [pscustomobject]@{
        Status          = [int]$response.StatusCode
        ContentType     = $contentType
        Body            = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    }
}

function Test-ApiImage {
    Write-Host "`n== $ApiImage (REST API) ==" -ForegroundColor Cyan
    $clock = [System.Diagnostics.Stopwatch]::StartNew()
    Invoke-Docker -Arguments @('run', '-d', '--name', $apiName, '--network', $network,
        '-p', "127.0.0.1:${ApiPort}:8080", '-p', "127.0.0.1:${ManagementPort}:8081",
        '-e', "SKODA_VIN=$vin", '-e', "SKODA_API_KEY=$apiKey", '-e', "SKODA_PUBLIC_API_URL=$upstreamUrl",
        $ApiImage) | Out-Null
    $app = "http://127.0.0.1:$ApiPort"
    $management = "http://127.0.0.1:$ManagementPort"

    $health = $null
    while ($clock.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        try { $health = Invoke-HttpGet "$management/actuator/health"; if ($health.Status -eq 200) { break } } catch { }
        Start-Sleep -Milliseconds 100
    }
    $script:facts['api: docker run -> health 200'] = "$($clock.ElapsedMilliseconds) ms"
    Check 'api management health 200 UP' ($null -ne $health -and $health.Status -eq 200 -and $health.Body -match '"status":"UP"') $(if ($health) { $health.Body } else { 'no answer' })
    $logs = (Invoke-Docker -Arguments @('logs', $apiName)) -join "`n"
    $script:facts['api: Spring startup (log)'] = "$(Get-StartedLine $logs) s"

    $r = Invoke-HttpGet "$app/actuator/health"
    Check 'api health not on app port' ($r.Status -eq 404) "status $($r.Status)"
    $r = Invoke-HttpGet "$app/api/v1/vehicle"
    $body = $null; try { $body = $r.Body | ConvertFrom-Json } catch { }
    Check 'api /api/v1/vehicle without credentials -> 200' ($r.Status -eq 200 -and $null -ne $body -and $body.data.name -eq 'Family Octavia' -and
        $body.data.range.totalRangeKm -eq 612 -and $body.data.status.odometerKm -eq 48213) "$($r.Status) $($r.Body.Substring(0, [math]::Min(160, $r.Body.Length)))..."
    $r = Invoke-HttpGet "$app/api/v1/vehicle/location"
    Check 'api /api/v1/vehicle/location -> 200' ($r.Status -eq 200 -and $r.Body -match '"state":"PARKED"') "$($r.Status) $($r.Body)"
    $r = Invoke-HttpGet "$app/api/v1/system/api-key"
    Check 'api /api/v1/system/api-key -> 200' ($r.Status -eq 200 -and $r.Body -match '"keyExpiresAt":"2026-12-31T00:00:00Z"') "$($r.Status) $($r.Body)"
    $r = Invoke-HttpGet "$app/skoda-service-api.yaml"
    Check 'api /skoda-service-api.yaml' ($r.Status -eq 200 -and $r.ContentType -like 'application/yaml*' -and $r.Body -match 'operationId: getVehicleOverview') "$($r.Status) $($r.ContentType)"
    $r = Invoke-HttpGet "$app/swagger-ui.html"
    Check 'api /swagger-ui.html (redirect to Swagger UI)' ($r.Status -eq 200 -and $r.Body -match 'swagger-ui') "$($r.Status) $($r.ContentType)"
    $r = Invoke-HttpGet "$app/swagger-ui/swagger-initializer.js"
    Check 'api Swagger UI points at our contract' ($r.Status -eq 200 -and $r.Body -match '/skoda-service-api.yaml') "$($r.Status)"

    $requests = @(Get-UpstreamRequests)
    Check 'api: upstream called once (snapshot cache)' ($requests.Count -eq 1) ($requests -join '; ')
    $script:facts['api: memory after requests'] = (Invoke-Docker -Arguments @('stats', '--no-stream', '--format', '{{.MemUsage}}', $apiName) | Select-Object -Last 1)
    $logs = (Invoke-Docker -Arguments @('logs', $apiName)) -join "`n"
    Check 'api log has no ERROR' (-not ($logs -match '\sERROR\s')) ''
}

# ------------------------------------------------------------------------------------------------
try {
    if ($Build) {
        Write-Host '== Building images ==' -ForegroundColor Cyan
        foreach ($variant in @(@{ Profile = 'mcp'; Tag = $McpImage }, @{ Profile = 'api'; Tag = $ApiImage })) {
            $clock = [System.Diagnostics.Stopwatch]::StartNew()
            & docker build --build-arg "BUILD_PROFILE=$($variant.Profile)" -t $variant.Tag $repo
            if ($LASTEXITCODE -ne 0) { throw "docker build ($($variant.Profile)) failed" }
            $script:facts["build $($variant.Tag)"] = "$([math]::Round($clock.Elapsed.TotalMinutes, 1)) min"
        }
    }

    Remove-SmokeContainers
    Invoke-Docker -Arguments @('network', 'create', $network) | Out-Null
    $stub = (Join-Path $repo 'scripts/smoke/upstream_stub.py')
    $fixture = (Join-Path $repo 'src/test/resources/upstream/vehicle-full.json')

    if (-not $SkipMcp) {
        Invoke-Docker -Arguments @('run', '-d', '--name', $upstreamName, '--network', $network,
            '-v', "${stub}:/stub/upstream_stub.py:ro", '-v', "${fixture}:/stub/vehicle.json:ro",
            'python:3.13-alpine', 'python', '/stub/upstream_stub.py', '/stub/vehicle.json', '8080') | Out-Null
        Test-McpImage
        Invoke-Docker -Arguments @('rm', '-f', $upstreamName) | Out-Null
    }
    if (-not $SkipApi) {
        # fresh stub, so its request log only contains the api run
        Invoke-Docker -Arguments @('run', '-d', '--name', $upstreamName, '--network', $network,
            '-v', "${stub}:/stub/upstream_stub.py:ro", '-v', "${fixture}:/stub/vehicle.json:ro",
            'python:3.13-alpine', 'python', '/stub/upstream_stub.py', '/stub/vehicle.json', '8080') | Out-Null
        Test-ApiImage
    }

    foreach ($image in @($McpImage, $ApiImage)) {
        $size = Invoke-Docker -Arguments @('image', 'inspect', $image) -AllowFailure
        if ($LASTEXITCODE -eq 0) { $script:facts["image size $image"] = "$(Get-ImageSizeMB $image) MB" }
    }
}
catch {
    Check 'smoke test run' $false $_.Exception.Message
}
finally {
    Remove-SmokeContainers
    $http.Dispose()
}

Write-Host "`n== Facts ==" -ForegroundColor Cyan
$script:facts.GetEnumerator() | ForEach-Object { Write-Host ("{0,-42} {1}" -f $_.Key, $_.Value) }
$failed = @($script:checks | Where-Object { $_.Result -eq 'FAIL' })
Write-Host ("`n{0} checks, {1} failed" -f $script:checks.Count, $failed.Count) -ForegroundColor $(if ($failed.Count) { 'Red' } else { 'Green' })
if ($failed.Count -gt 0) { exit 1 }
exit 0

