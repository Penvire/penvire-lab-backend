// Shared Java settings for every module.
plugins {
    java
}

group = "com.penvire.lab"

// release-please writes each service's version to its version.txt.
val versionFile = layout.projectDirectory.file("version.txt").asFile
version = if (versionFile.exists()) versionFile.readText().trim() else "0.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all,-processing", "-Werror"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
