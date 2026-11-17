// Every Java module: toolchain, Error Prone with NullAway, Spotless, JaCoCo,
// Lombok and JSpecify.
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    `java-library`
    jacoco
    id("net.ltgt.errorprone")
    id("com.diffplug.spotless")
}

val libs = versionCatalogs.named("libs")

group = "com.penvire.lab"

// release-please writes each component's version to its version.txt.
val versionFile = layout.projectDirectory.file("version.txt").asFile
version = if (versionFile.exists()) versionFile.readText().trim() else "0.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
    options.errorprone {
        disableWarningsInGeneratedCode = true
        check("NullAway", CheckSeverity.ERROR)
        option("NullAway:AnnotatedPackages", "com.penvire")
        option("NullAway:JSpecifyMode", "true")
        option(
            "NullAway:ExcludedClassAnnotations",
            "javax.annotation.processing.Generated,jakarta.annotation.Generated",
        )
        // Fields set by injection count as initialized.
        option(
            "NullAway:ExcludedFieldAnnotations",
            "jakarta.inject.Inject,org.eclipse.microprofile.config.inject.ConfigProperty," +
                "io.quarkus.test.common.http.TestHTTPResource,io.quarkus.test.InjectMock",
        )
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    finalizedBy(tasks.named("jacocoTestReport"))
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.test)
}

tasks.named("check") {
    dependsOn(tasks.named("jacocoTestReport"))
    dependsOn(tasks.named("spotlessCheck"))
}

spotless {
    java {
        googleJavaFormat(libs.findVersion("google-java-format").get().requiredVersion)
    }
}

jacoco {
    toolVersion = libs.findVersion("jacoco").get().requiredVersion
}

dependencies {
    compileOnly(libs.findLibrary("lombok").get())
    annotationProcessor(libs.findLibrary("lombok").get())
    compileOnly(libs.findLibrary("jspecify").get())
    errorprone(libs.findLibrary("errorprone-core").get())
    errorprone(libs.findLibrary("nullaway").get())

    testCompileOnly(libs.findLibrary("lombok").get())
    testAnnotationProcessor(libs.findLibrary("lombok").get())
    testCompileOnly(libs.findLibrary("jspecify").get())
}
