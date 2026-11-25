import com.diffplug.gradle.spotless.SpotlessExtension
import com.penvire.buildlogic.openapi.OpenApiServerExtension
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    java
    id("org.openapi.generator")
}

val openApiServer = extensions.create<OpenApiServerExtension>("openApiServer")
val generatedServerPath = "generated/openapi/server"

val generateOpenApiServer = tasks.register<GenerateTask>("generateOpenApiServer") {
    group = "openapi"
    description = "Generates Quarkus REST interfaces and models from the service contract."
    generatorName = "jaxrs-spec"
    library = "quarkus"
    inputSpec = openApiServer.sourceSpec
    outputDir = layout.buildDirectory.dir(generatedServerPath)
    apiPackage = openApiServer.serverPackage.map { "$it.api" }
    modelPackage = openApiServer.serverPackage.map { "$it.model" }
    cleanupOutput = true
    generateApiTests = false
    generateModelTests = false
    generateApiDocumentation = false
    generateModelDocumentation = false
    globalProperties = mapOf("apis" to "", "models" to "", "supportingFiles" to "package-info.java")
    configOptions = mapOf(
        "interfaceOnly" to "true",
        "useTags" to "true",
        "useJakartaEe" to "true",
        "useJspecify" to "true",
        "useBeanValidation" to "false",
        "openApiNullable" to "false",
        "additionalModelTypeAnnotations" to "@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)",
        "hideGenerationTimestamp" to "true",
        "sourceFolder" to "src/gen/java",
    )
}

sourceSets.main {
    java.srcDir(generateOpenApiServer.flatMap { it.outputDir.dir("src/gen/java") })
}

pluginManager.withPlugin("com.diffplug.spotless") {
    extensions.configure<SpotlessExtension> {
        java {
            targetExclude(layout.buildDirectory.dir(generatedServerPath).map { it.asFileTree })
        }
    }
}
