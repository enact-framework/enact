plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("buildsrc.convention.enact-publish")
}

description = "Spring Boot starter exposing Enact use cases as HTTP endpoints."

dependencies {
    api(project(":enact-starter"))
    api(libs.springBootStarterWeb)
    implementation(libs.jacksonKotlin)
    implementation(libs.springBootAutoconfigure)

    testImplementation(libs.springBootStarterTest)
    testImplementation(libs.springBootStarterWebmvcTest)
}
