pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "penvire-lab-backend"

// Libs
include(":libs:observability")
include(":libs:problem")
include(":libs:jwt")

// Services
include(":services:identity")
