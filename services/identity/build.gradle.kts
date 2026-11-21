plugins {
    id("penvire.quarkus-service")
}

dependencies {
    implementation(project(":libs:jwt"))
}
