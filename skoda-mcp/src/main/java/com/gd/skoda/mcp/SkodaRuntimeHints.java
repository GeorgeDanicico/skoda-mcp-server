package com.gd.skoda.mcp;

import be.nicholasmeyers.skodaconnector.web.out.IdentityClient;
import be.nicholasmeyers.skodaconnector.web.out.TokenClient;
import com.gd.skoda.api.client.Vehicle;
import com.gd.skoda.api.client.VehicleAirConditioningStatus;
import com.gd.skoda.api.client.VehicleChargingSession;
import com.gd.skoda.api.client.VehicleChargingState;
import com.gd.skoda.api.client.VehicleLocation;
import com.gd.skoda.api.client.VehicleRange;
import com.gd.skoda.api.client.VehicleStatus;
import com.gd.skoda.client.resource.AirConditioningWebRequestResource;
import com.gd.skoda.client.resource.AirConditioningWebResponseResource;
import com.gd.skoda.client.resource.ChargingSessionWebResponseResource;
import com.gd.skoda.client.resource.ChargingStatusWebResponseResource;
import com.gd.skoda.client.resource.LocationWebResponseResource;
import com.gd.skoda.client.resource.ProblemDetailResponseResource;
import com.gd.skoda.client.resource.RangeWebResponseResource;
import com.gd.skoda.client.resource.StatusWebResponseResource;
import com.gd.skoda.client.resource.VehicleAccessSecurityWebRequestResource;
import com.gd.skoda.client.resource.VehicleWebResponseResource;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.ai.mcp.annotation.context.DefaultMetaProvider;

/**
 * Native-image metadata for libraries that deserialize HTTP payloads at runtime.
 * Spring AOT discovers this application's controllers and MCP tools itself, but
 * Gson, Jackson/Feign, and Feign's interface proxy cannot be inferred from the
 * third-party Skoda client.
 */
public final class SkodaRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        // Spring AI's MCP annotation processor creates this provider through
        // Class#getConstructor/newInstance while assembling tool metadata.
        hints.reflection().registerType(DefaultMetaProvider.class,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
        registerGsonPayload(hints,
                VehicleWebResponseResource.class,
                LocationWebResponseResource.class,
                StatusWebResponseResource.class,
                RangeWebResponseResource.class,
                ChargingStatusWebResponseResource.class,
                ChargingSessionWebResponseResource.class,
                AirConditioningWebRequestResource.class,
                AirConditioningWebResponseResource.class,
                VehicleAccessSecurityWebRequestResource.class,
                ProblemDetailResponseResource.class);
        registerBindingType(hints,
                Vehicle.class, VehicleLocation.class, VehicleRange.class, VehicleStatus.class,
                VehicleAirConditioningStatus.class, VehicleChargingState.class, VehicleChargingSession.class,
                VehicleSnapshot.class, VehicleSnapshot.Location.class);
        registerBindingType(hints,
                be.nicholasmeyers.skodaconnector.web.out.resource.OpenidConfigurationWebResponseResource.class,
                be.nicholasmeyers.skodaconnector.web.out.resource.PostEmailWebRequestResource.class,
                be.nicholasmeyers.skodaconnector.web.out.resource.PostEmailPasswordWebRequestResource.class,
                be.nicholasmeyers.skodaconnector.web.out.resource.CodeExchangeWebRequestResource.class,
                be.nicholasmeyers.skodaconnector.web.out.resource.TokenWebResponseResource.class);
        // Registering the interfaces retains method introspection, which Feign
        // uses to parse their HTTP annotations; no reflective invocation is needed.
        hints.reflection().registerType(IdentityClient.class);
        hints.reflection().registerType(TokenClient.class);
        hints.proxies().registerJdkProxy(IdentityClient.class);
        hints.proxies().registerJdkProxy(TokenClient.class);
    }

    private void registerGsonPayload(RuntimeHints hints, Class<?>... types) {
        for (Class<?> type : types) {
            hints.reflection().registerType(type,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.ACCESS_DECLARED_FIELDS);
        }
    }

    private void registerBindingType(RuntimeHints hints, Class<?>... types) {
        for (Class<?> type : types) {
            hints.reflection().registerType(type,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_PUBLIC_METHODS,
                    MemberCategory.ACCESS_DECLARED_FIELDS);
        }
    }
}
