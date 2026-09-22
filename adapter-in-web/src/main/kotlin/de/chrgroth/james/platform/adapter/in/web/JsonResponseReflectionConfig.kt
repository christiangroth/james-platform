package de.chrgroth.james.platform.adapter.`in`.web

import io.quarkus.runtime.annotations.RegisterForReflection

// Jackson (via jackson-module-kotlin) resolves a Kotlin data class' primary constructor reflectively
// (kotlin.reflect.full.KClasses.getPrimaryConstructor) when serializing a JAX-RS Response body whose concrete type
// Quarkus's build-time endpoint-return-type scanner can't infer (every REST endpoint below returns the generic
// jakarta.ws.rs.core.Response, built via Response.ok(SomeDataClass(...)), so Quarkus never sees the concrete
// payload type statically and doesn't auto-register it). In native mode this throws
// KotlinReflectionInternalError: Unresolved class - a 500 that only ever occurs in native mode, never on the JVM,
// exactly like the Qute @TemplateData gap this migration is otherwise about (see #672). Request-body classes using
// an explicit @JsonCreator constructor (e.g. SortCriteriaRequest, FilterRuleRequest) don't need this - Jackson
// doesn't need Kotlin reflection when the constructor mapping is spelled out explicitly.
@RegisterForReflection(
  targets = [
    ApiResult::class,
    UserStatusResponse::class,
    DeveloperApiResult::class,
    TestDataGenerationStatusResponse::class,
    AppStatusResponse::class,
    VersionBumpResponse::class,
    FilterSampleResponse::class,
    MappingSampleResponse::class,
    DryRunObjectRow::class,
    DryRunPropertyRow::class,
    DryRunIssueRow::class,
    InstalledAppStatusResponse::class,
  ],
)
class JsonResponseReflectionConfig
