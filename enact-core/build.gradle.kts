plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("buildsrc.convention.enact-publish")
}

description = "Core of Enact: steps, use cases, validation and execution."

dependencies {
    api(libs.springContext)

    testImplementation(libs.junitJupiter)
    testImplementation(libs.springTest)
}
