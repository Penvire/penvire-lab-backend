plugins {
    id("penvire.quarkus-library")
    id("io.quarkus") version libs.versions.quarkus.get()
}

dependencies {
    api("io.quarkus:quarkus-hibernate-orm-panache")
    api("io.quarkus:quarkus-jdbc-postgresql")
    api("io.quarkus:quarkus-flyway")
    implementation(libs.jug)
}