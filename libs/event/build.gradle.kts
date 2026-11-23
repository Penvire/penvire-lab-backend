plugins {
    id("penvire.quarkus-library")
    id("io.quarkus") version libs.versions.quarkus.get()
}

dependencies {
    // Publishers pass generated Avro records; the outbox shares the caller's transaction.
    api("org.apache.avro:avro")
    api(project(":libs:persistence"))
    api("io.quarkus:quarkus-messaging-kafka")

    implementation(project(":libs:clock"))
    // The outbox row keeps the traceparent of the request that wrote it.
    implementation("io.quarkus:quarkus-opentelemetry")
    // The relay polls the outbox on a schedule.
    implementation("io.quarkus:quarkus-scheduler")

    testImplementation(testFixtures(project(":libs:clock")))
    testImplementation("org.awaitility:awaitility")
}
