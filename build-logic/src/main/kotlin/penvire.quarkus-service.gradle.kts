// A Quarkus service: REST, health endpoints and Quarkus tests.
plugins {
    id("penvire.java-conventions")
    id("io.quarkus")
}

val libs = versionCatalogs.named("libs")

dependencies {
    implementation(enforcedPlatform(libs.findLibrary("quarkus-bom").get()))
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-smallrye-health")

    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")
}
