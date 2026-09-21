plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("buildsrc.convention.enact-publish")
    kotlin("kapt")
}

description = "Spring Boot auto-configuration for Enact use cases defined in YAML."

dependencies {
    api(project(":enact-core"))
    implementation(libs.springBootAutoconfigure)
    kapt(libs.springBootConfigurationProcessor)

    testImplementation(libs.springBootStarterTest)
    testImplementation(libs.micrometerObservationTest)
    testImplementation(libs.micrometerCore)
}
