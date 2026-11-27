plugins {
    id("penvire.quarkus-library")
    id("io.quarkus") version libs.versions.quarkus.get()
    `java-test-fixtures`
}

dependencies {
    implementation(libs.openfga.sdk)
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-security")

    testFixturesApi(libs.jspecify)
    testFixturesImplementation("io.quarkus:quarkus-security")

    testImplementation(libs.jackson.databind)
    testImplementation("org.testcontainers:testcontainers")
}
