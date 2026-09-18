// Publishes a library module to Maven Central. Shared POM data (group, version, license, SCM) lives in gradle.properties.
package buildsrc.convention

plugins {
    id("com.vanniktech.maven.publish")
}

mavenPublishing {
    pom {
        name.set(project.name)
        description.set(provider { project.description })
    }
}
