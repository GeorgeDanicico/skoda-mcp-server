package com.gd.skoda.mcp;

import com.gd.skoda.error.ProblemTypes;
import com.gd.skoda.error.SkodaApiProblems;
import com.gd.skoda.upstream.SkodaApiException;
import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Runs the work of an MCP tool and turns failures into {@link McpToolException}s, which Spring AI
 * reports to the client as tool errors ({@code isError=true}) rather than protocol errors, so the
 * model sees what went wrong and what to do about it.
 *
 * <ul>
 *   <li>{@link SkodaApiException}: mapped with {@link SkodaApiProblems} (same title, detail,
 *       {@code retryAfterSeconds} and {@code upstreamStatus} as the REST API) plus advice on whether a
 *       retry helps. The detail never contains the API key.</li>
 *   <li>Anything else: a generic internal error without internal details; the exception is logged.</li>
 * </ul>
 *
 * The generated tool classes call this for every tool, so the translation lives here only once.
 */
public final class McpToolErrors {

    private static final Logger log = LoggerFactory.getLogger(McpToolErrors.class);

    /** Problems that go away by themselves; everything else needs the operator. */
    private static final Set<URI> TEMPORARY = Set.of(ProblemTypes.RATE_LIMIT_EXCEEDED,
            ProblemTypes.UPSTREAM_UNAVAILABLE, ProblemTypes.UPSTREAM_TIMEOUT);

    private final SkodaApiProblems problems;

    public McpToolErrors(SkodaApiProblems problems) {
        this.problems = problems;
    }

    /**
     * Returns the result of {@code operation}.
     *
     * @throws McpToolException if the operation fails
     */
    public <T> T call(String toolName, Supplier<T> operation) {
        try {
            return operation.get();
        } catch (SkodaApiException ex) {
            throw toToolError(toolName, ex);
        } catch (McpToolException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw unexpected(toolName, ex);
        }
    }

    /**
     * Runs {@code operation} (tools without a result).
     *
     * @throws McpToolException if the operation fails
     */
    public void run(String toolName, Runnable operation) {
        call(toolName, () -> {
            operation.run();
            return null;
        });
    }

    /** The tool error for a failed MyŠkoda call. */
    public McpToolException toToolError(String toolName, SkodaApiException ex) {
        ProblemDetail problem = problems.toProblem(ex);
        log.warn("Tool {} failed with {}: {}", toolName, problem.getType(), problem.getDetail());
        return new McpToolException(headline(toolName, problem), details(problem), problem);
    }

    private McpToolException unexpected(String toolName, RuntimeException ex) {
        log.error("Tool {} failed unexpectedly", toolName, ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred in the Skoda MCP server. Details are in the server log.");
        problem.setType(ProblemTypes.INTERNAL_ERROR);
        problem.setTitle("Internal server error");
        return new McpToolException(headline(toolName, problem), details(problem), problem);
    }

    private static String headline(String toolName, ProblemDetail problem) {
        return toolName + " failed: " + problem.getTitle() + ".";
    }

    private static String details(ProblemDetail problem) {
        Optional<Long> retryAfterSeconds = SkodaApiProblems.retryAfterSeconds(problem);
        StringJoiner facts = new StringJoiner(", ", " [", "]");
        facts.add("problem: " + problem.getType());
        retryAfterSeconds.ifPresent(seconds -> facts.add(SkodaApiProblems.RETRY_AFTER_SECONDS + ": " + seconds));
        if (problem.getProperties() != null
                && problem.getProperties().get(SkodaApiProblems.UPSTREAM_STATUS) instanceof Integer status) {
            facts.add(SkodaApiProblems.UPSTREAM_STATUS + ": " + status);
        }
        return problem.getDetail() + " " + advice(problem.getType(), retryAfterSeconds) + facts;
    }

    private static String advice(URI type, Optional<Long> retryAfterSeconds) {
        if (TEMPORARY.contains(type)) {
            return retryAfterSeconds
                    .map(seconds -> "This is temporary: do not call the tool again for " + seconds + " seconds.")
                    .orElse("This is temporary: try again later.");
        }
        if (ProblemTypes.INTERNAL_ERROR.equals(type)) {
            return "Retrying is unlikely to help.";
        }
        return "Retrying will not help: the server operator has to fix this, so tell the user.";
    }
}
