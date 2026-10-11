package com.gd.skoda.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.gd.skoda.SkodaApplication;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.context.annotation.ImportRuntimeHints;

class NativeHintsTest {

    @Test
    void allowsBeanValidationOfTheConfigurationRecords() throws Exception {
        RuntimeHints hints = new RuntimeHints();
        new NativeHints().registerHints(hints, getClass().getClassLoader());

        assertThat(NativeHints.VALIDATED_PROPERTIES).containsExactlyInAnyOrder(SkodaProperties.class,
                SkodaProperties.PublicApi.class, SkodaProperties.Cache.class);
        for (Class<?> type : NativeHints.VALIDATED_PROPERTIES) {
            assertThat(RuntimeHintsPredicates.reflection().onType(type)
                    .withMemberCategories(MemberCategory.ACCESS_DECLARED_FIELDS, MemberCategory.INVOKE_PUBLIC_METHODS))
                    .accepts(hints);
        }
        assertThat(RuntimeHintsPredicates.reflection().onFieldAccess(SkodaProperties.Cache.class.getDeclaredField("ttl")))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(SkodaProperties.PublicApi.class.getMethod("key")))
                .accepts(hints);
    }

    @Test
    void isImportedByTheApplication() {
        assertThat(SkodaApplication.class.getAnnotation(ImportRuntimeHints.class).value())
                .contains(NativeHints.class);
    }
}
