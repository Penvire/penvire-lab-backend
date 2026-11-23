// A Quarkus service: REST, health, OpenTelemetry, MapStruct, Quarkus tests and the audit manifest.
import com.penvire.buildlogic.audit.AuditManifestDiffTask
import com.penvire.buildlogic.audit.AuditManifestTask

plugins {
    id("penvire.quarkus-library")
    id("io.quarkus")
}

tasks.named("quarkusDev") {
    enabled = true
}

val libs = versionCatalogs.named("libs")

dependencies {
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-smallrye-health")

    // Traces, metrics and logs over OTLP; the default endpoint
    // (localhost:4317) is the devenv collector.
    implementation("io.quarkus:quarkus-opentelemetry")

    implementation(libs.findLibrary("mapstruct").get())
    annotationProcessor(libs.findLibrary("mapstruct-processor").get())
    annotationProcessor(libs.findLibrary("lombok-mapstruct-binding").get())

    testImplementation("io.rest-assured:rest-assured")
}

// Shared config defaults for every service, in every mode (dev, test, prod).
// microprofile-config.properties ranks below the service's application.properties.
val configDefaults = tasks.register<WriteProperties>("penvireConfigDefaults") {
    destinationFile = layout.buildDirectory.file("generated/penvire-config/META-INF/microprofile-config.properties")
    property("quarkus.otel.metrics.enabled", "true")
    property("quarkus.otel.logs.enabled", "true")
    // Use the shared devenv stack, not a Grafana LGTM container per service.
    property("quarkus.observability.enabled", "false")
}
sourceSets.main {
    resources.srcDir(configDefaults.map { it.destinationFile.get().asFile.parentFile.parentFile })
}

// Tests export nothing; no collector is needed to run them.
tasks.withType<Test>().configureEach {
    systemProperty("quarkus.otel.sdk.disabled", "true")
}

// What the service's @Audited methods record (ADR 0006), committed beside the build file.
// The build regenerates it; CI fails if the committed file differs.
val auditManifest = tasks.register<AuditManifestTask>("auditManifest") {
    group = "build"
    description = "Writes audit-manifest.yaml from the service's @Audited methods."
    classesDirs.from(sourceSets.main.map { it.output.classesDirs })
    classpath.from(configurations.runtimeClasspath)
    manifest = layout.projectDirectory.file("audit-manifest.yaml")
}
tasks.named("build") {
    dependsOn(auditManifest)
}

// For the pull request comment: compares the committed manifest with the base branch's, found
// under the checkout given as -PauditManifestBase=<dir> (.github/workflows/audit-manifest.yml).
tasks.register<AuditManifestDiffTask>("auditManifestDiff") {
    group = "verification"
    description = "Compares audit-manifest.yaml with the base branch's, as Markdown."
    val manifestPath = layout.projectDirectory.file("audit-manifest.yaml").asFile.relativeTo(rootDir).path
    service = project.name
    base.from(providers.gradleProperty("auditManifestBase").map { listOf("$it/$manifestPath") }.orElse(emptyList()))
    head.from(layout.projectDirectory.file("audit-manifest.yaml"))
    markdown = layout.buildDirectory.file("audit-manifest-diff.md")
}
