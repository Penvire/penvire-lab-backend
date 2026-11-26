pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        exclusiveContent {
            forRepository {
                maven("https://git.qf0xb.de/api/packages/penvire/maven")
            }
            filter {
                includeGroup("dev.penvire.lab")
            }
        }
    }
}

rootProject.name = "penvire-lab-backend"

// Libs
include(":libs:clock")
include(":libs:observability")
include(":libs:persistence")
include(":libs:problem")
include(":libs:jwt")
include(":libs:event")
include(":libs:audit")
include(":libs:security")

// Services
include(":services:identity")
