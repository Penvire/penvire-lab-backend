// A Quarkus service: REST, health, OpenTelemetry, MapStruct and Quarkus tests.
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
