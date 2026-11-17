plugins {
    id("penvire.quarkus-library")
    id("io.quarkus") version libs.versions.quarkus.get()
}

dependencies {
    implementation("io.quarkus:quarkus-opentelemetry")
    implementation("io.quarkus:quarkus-rest")
    testImplementation("io.rest-assured:rest-assured")
}
