plugins {
    id("penvire.quarkus-library")
    id("io.quarkus") version libs.versions.quarkus.get()
    `java-test-fixtures`
}

dependencies {
    implementation("io.quarkus:quarkus-arc")
}
