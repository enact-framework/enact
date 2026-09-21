plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("buildsrc.convention.enact-publish")
}

description = "Core of Enact: steps, use cases, validation and execution."

dependencies {
    api(libs.springContext)
    api(libs.micrometerObservation)
    implementation(libs.micrometerContextPropagation)

    testImplementation(libs.junitJupiter)
    testImplementation(libs.springTest)
    testImplementation(libs.micrometerObservationTest)
}
