plugins {
    id("penvire.java-library")
}

dependencies {
    api(libs.jackson.databind)

    testImplementation(testFixtures(project(":libs:clock")))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
