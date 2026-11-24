// build-logic is an included build, so its tests would not run with ./gradlew build otherwise.
tasks.register("build") {
    dependsOn(gradle.includedBuild("build-logic").task(":build"))
}
