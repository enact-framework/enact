plugins {
    id("buildsrc.convention.kotlin-jvm")
}

group = "io.enact"

dependencies {
    implementation(libs.springContext)
    implementation(libs.springWeb)
    implementation(libs.jacksonYaml)
    implementation(libs.jacksonKotlin)

    testImplementation(libs.junitJupiter)
    testImplementation(libs.springTest)
}
