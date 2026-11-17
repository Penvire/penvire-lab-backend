plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.errorprone.gradle.plugin)
    implementation(libs.quarkus.gradle.plugin)
    implementation(libs.spotless.gradle.plugin)
}
