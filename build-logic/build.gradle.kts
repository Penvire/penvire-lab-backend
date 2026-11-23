plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.errorprone.gradle.plugin)
    implementation(libs.openapi.generator.gradle.plugin)
    implementation(libs.quarkus.gradle.plugin)
    implementation(libs.spotless.gradle.plugin)
    // Reads the services' @Audited methods for their audit manifests.
    implementation(libs.jandex)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // What libs/audit's Audited needs to compile: the manifest tests use the real annotation.
    testImplementation(platform(libs.quarkus.bom))
    testImplementation("io.quarkus.security:quarkus-security")
    testImplementation("jakarta.enterprise:jakarta.enterprise.cdi-api")
    testImplementation("jakarta.interceptor:jakarta.interceptor-api")
}

tasks.test {
    useJUnitPlatform()
    val auditSources = layout.projectDirectory.dir("../libs/audit/src/main/java/com/penvire/lab/audit")
    inputs.dir(auditSources).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("penvire.audit.sources", auditSources.asFile.absolutePath)
}
