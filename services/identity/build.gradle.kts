plugins {
    id("penvire.quarkus-service")
}

dependencies {
    implementation(project(":libs:jwt"))
}

tasks.test {
    inputs.property("runOpenSslInterop", providers.environmentVariable("RUN_OPENSSL_INTEROP").orElse("0"))
}
