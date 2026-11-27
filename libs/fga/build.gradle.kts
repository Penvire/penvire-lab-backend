plugins {
    id("penvire.quarkus-library")
    id("io.quarkus") version libs.versions.quarkus.get()
    `java-test-fixtures`
}

dependencies {
    implementation(project(":libs:problem"))
    implementation(libs.openfga.sdk) {
        // openfga-sdk pulls in opentelemetry-bom 1.67.0, upgrading Quarkus's OTel SDK past its
        // pinned opentelemetry-api-incubator 1.62.0-alpha. Let the Quarkus BOM own OpenTelemetry.
        exclude(group = "io.opentelemetry")
    }
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-security")

    testFixturesApi(libs.jspecify)
    testFixturesImplementation("io.quarkus:quarkus-security")

    testImplementation(libs.jackson.databind)
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("org.testcontainers:testcontainers")
}
