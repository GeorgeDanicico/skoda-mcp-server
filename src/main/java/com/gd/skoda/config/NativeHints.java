package com.gd.skoda.config;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Native-image hints that Spring AOT cannot derive itself.
 *
 * <p>Verified with the native executables (scripts/smoke-test-native.ps1) and {@code -PnativeTest}.
 * Already covered elsewhere, so not repeated here:
 * <ul>
 *   <li>the generated tool classes (all members) and the {@code metaProvider} classes of their
 *       {@code @McpTool}s, including {@code DefaultMetaProvider} (declared constructors): Spring AI's
 *       {@code ServerAnnotatedBeanFactoryInitializationAotProcessor};</li>
 *   <li>the tool result DTOs (Jackson serialisation and output-schema generation, including their
 *       {@code @Schema} annotations): {@code @RegisterReflectionForBinding} on each generated tool method;</li>
 *   <li>the generated {@code VehiclesApi} HTTP interface: JDK proxy and method hints plus binding hints
 *       for {@code VehicleResponse} and the nested upstream models, from Spring AOT;</li>
 *   <li>REST controllers and DTOs, {@code static/**} resources, logback configuration, the MCP SDK's
 *       {@code ServiceLoader} providers and {@code @ConfigurationProperties} binding: Spring (Boot) AOT,
 *       GraalVM and the libraries' own metadata.</li>
 * </ul>
 */
public final class NativeHints implements RuntimeHintsRegistrar {

    /** {@code @Validated} configuration records; see {@link #registerHints}. */
    static final List<Class<?>> VALIDATED_PROPERTIES = List.of(SkodaProperties.class,
            SkodaProperties.PublicApi.class, SkodaProperties.Cache.class);

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        // Hibernate Validator validates the @Validated configuration records at startup by reading the
        // constrained record components reflectively (fields and accessors). Boot's binding hints cover only
        // the canonical constructors, so without these the executables fail with a
        // MissingReflectionRegistrationError (first seen on SkodaProperties$Cache.ttl).
        for (Class<?> type : VALIDATED_PROPERTIES) {
            hints.reflection().registerType(type, MemberCategory.ACCESS_DECLARED_FIELDS,
                    MemberCategory.INVOKE_PUBLIC_METHODS);
        }
    }
}
