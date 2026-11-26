plugins {
    id("penvire.quarkus-library")
    id("io.quarkus") version libs.versions.quarkus.get()
}

dependencies {
    implementation(project(":libs:jwt"))
    implementation(project(":libs:clock"))
    implementation("io.quarkus:quarkus-security")
    implementation("io.quarkus:quarkus-vertx-http")

    testImplementation(testFixtures(project(":libs:clock")))
    testImplementation("io.quarkus:quarkus-rest")
    testImplementation("io.rest-assured:rest-assured")
}
