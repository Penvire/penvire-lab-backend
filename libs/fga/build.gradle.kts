plugins {
    id("penvire.quarkus-library")
    id("io.quarkus") version libs.versions.quarkus.get()
    `java-test-fixtures`
}

val fgaModel = configurations.dependencyScope("fgaModel")
val fgaModelFiles = configurations.resolvable("fgaModelFiles") {
    extendsFrom(fgaModel.get())
}

dependencies {
    // FgaException extends ProblemException, so callers need it on their compile classpath.
    api(project(":libs:problem"))
    implementation(libs.openfga.sdk) {
        // openfga-sdk pulls in opentelemetry-bom 1.67.0, upgrading Quarkus's OTel SDK past its
        // pinned opentelemetry-api-incubator 1.62.0-alpha. Let the Quarkus BOM own OpenTelemetry.
        exclude(group = "io.opentelemetry")
    }
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-security")

    add(fgaModel.name, "dev.penvire.lab:fga-model:${libs.versions.fga.model.get()}@json")

    testFixturesApi(libs.jspecify)
    testFixturesImplementation("io.quarkus:quarkus-security")
    testFixturesImplementation(libs.openfga.sdk) {
        exclude(group = "io.opentelemetry")
    }
    testFixturesImplementation(libs.jackson.databind)
    testFixturesImplementation("org.testcontainers:testcontainers")

    testImplementation(libs.jackson.databind)
    testImplementation("io.rest-assured:rest-assured")
}

val fgaModelVersion = libs.versions.fga.model.get()

val writeFgaModelMetadata = tasks.register<WriteProperties>("writeFgaModelMetadata") {
    destinationFile = layout.buildDirectory.file("generated/fga-model-meta/fga/model.properties")
    property("version", fgaModelVersion)
}

val prepareFgaModel = tasks.register<Sync>("prepareFgaModel") {
    into(layout.buildDirectory.dir("generated/fga-model"))
    from(fgaModelFiles) {
        into("fga")
        rename { "model.json" }
    }
    from(writeFgaModelMetadata.map { it.destinationFile.get().asFile.parentFile.parentFile })
}

sourceSets.main {
    resources.srcDir(prepareFgaModel)
}
