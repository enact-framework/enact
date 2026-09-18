dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "enact"

include(":enact-core")
include(":enact-starter")
include(":enact-starter-web")
include(":enact-demo")