plugins {
    id("penvire.quarkus-service")
    id("penvire.openapi-server")
}

val identityContract = configurations.dependencyScope("identityContract")
val identityContractFiles = configurations.resolvable("identityContractFiles") {
    extendsFrom(identityContract.get())
}

dependencies {
    implementation(project(":libs:clock"))
    implementation(project(":libs:jwt"))
    // Signing keys are stored in Identity's PostgreSQL database (ADR 0007).
    implementation(project(":libs:persistence"))
    implementation("io.quarkus:quarkus-rest-jackson")
    testImplementation(testFixtures(project(":libs:clock")))
    testImplementation(libs.openapi.request.validator) {
        // Identity only validates OpenAPI 3.1; Swagger 2 conversion pulls in json-patch.
        exclude(group = "io.swagger.parser.v3", module = "swagger-parser-v2-converter")
    }
    add(identityContract.name, "dev.penvire.lab:identity-openapi:${libs.versions.identity.contract.get()}@yaml")
}

openApiServer {
    sourceSpec = layout.file(identityContractFiles.flatMap { it.elements }.map { it.single().asFile })
    serverPackage = "com.penvire.lab.identity.server"
}

val prepareTestContract = tasks.register<Sync>("prepareTestContract") {
    into(layout.buildDirectory.dir("generated/test-contract"))
    from(identityContractFiles) {
        into("contracts")
        rename { "identity-openapi.yaml" }
    }
}

// Quarkus continuous testing discovers resources from the test source set.
sourceSets.test {
    resources.srcDir(prepareTestContract)
}

tasks.test {
    inputs.property("runOpenSslInterop", providers.environmentVariable("RUN_OPENSSL_INTEROP").orElse("0"))
}
