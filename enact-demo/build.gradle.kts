plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.springBoot)
    alias(libs.plugins.kotlinSpring)
}

dependencies {
    implementation(project(":enact-starter-web"))
    implementation(libs.springBootStarterCache)
    implementation(libs.springBootStarterActuator)

    testImplementation(libs.springBootStarterTest)
}
