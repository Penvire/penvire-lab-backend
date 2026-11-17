// Shared Java settings for every module.
plugins {
    java
}

group = "com.penvire.lab"

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
