import net.ltgt.gradle.errorprone.errorprone

plugins {
    id("penvire.quarkus-library")
    id("io.quarkus") version libs.versions.quarkus.get()
}

dependencies {
    // Audit events go through the service's outbox, in the use case's transaction (ADR 0006).
    implementation(project(":libs:event"))
    // The actor comes from the request's authenticated caller.
    implementation("io.quarkus:quarkus-security")
    // Tells an unauthenticated HTTP caller (ANONYMOUS) from work outside a request (SYSTEM).
    implementation("io.quarkus:quarkus-vertx-http")

    testImplementation("io.quarkus:quarkus-test-security")
    testImplementation("io.quarkus:quarkus-rest")
    testImplementation("io.rest-assured:rest-assured")
    // Generates the AuditRecorded class from src/test/avro, like a contract package ships it.
    testImplementation("io.quarkus:quarkus-avro")
}

// Avro marks its classes @AvroGenerated, not @Generated, so Error Prone would check them.
tasks.named<JavaCompile>("compileTestJava") {
    options.errorprone.excludedPaths = ".*/quarkus-test-generated-sources/.*"
}
