// A library used by Quarkus services (libs/*). It may apply io.quarkus for
// @QuarkusTest, but it never runs in dev mode itself.
plugins {
    id("penvire.java-library")
}

val libs = versionCatalogs.named("libs")

dependencies {
    api(platform(libs.findLibrary("quarkus-bom").get()))

    testImplementation("io.quarkus:quarkus-junit5")
    // Coverage for classes loaded by Quarkus' class loader in @QuarkusTest.
    testImplementation("io.quarkus:quarkus-jacoco")
}

// Unit tests and @QuarkusTest write to the same execution data; the Gradle
// agent skips Quarkus' class loader, which quarkus-jacoco instruments instead.
val jacocoData = layout.buildDirectory.file("jacoco/test.exec")
tasks.withType<Test>().configureEach {
    extensions.configure<JacocoTaskExtension> {
        excludeClassLoaders = listOf("*QuarkusClassLoader")
    }
}

pluginManager.withPlugin("io.quarkus") {
    tasks.named("quarkusDev") {
        enabled = false
    }
    extensions.configure<io.quarkus.gradle.extension.QuarkusPluginExtension> {
        set("jacoco.data-file", jacocoData.get().asFile.absolutePath)
        set("jacoco.reuse-data-file", "true")
        set("jacoco.report", "false")
    }
}
