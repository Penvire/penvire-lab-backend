plugins {
    id("penvire.quarkus-library")
    id("io.quarkus") version libs.versions.quarkus.get()
}

dependencies {
    // Only libs/problem sees the extension; other modules extend ProblemException.
    implementation("io.quarkiverse.httpproblem:quarkus-http-problem")
    implementation(project(":libs:observability"))
    implementation("io.quarkus:quarkus-rest-jackson")
    testImplementation("io.quarkus:quarkus-hibernate-validator")
    testImplementation("io.rest-assured:rest-assured")
}
