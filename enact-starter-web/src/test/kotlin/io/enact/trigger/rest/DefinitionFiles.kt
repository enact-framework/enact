package io.enact.trigger.rest

import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.io.path.writeText

/**
 * Writes [yaml] as a definition file and returns the location pointing at it.
 *
 * Use cases are no longer configuration properties, so a test declares them the way an application does.
 */
fun definitionsFile(yaml: String): String {
    val file = Files.createTempFile("enact-definitions-", ".yaml")
    file.toFile().deleteOnExit()
    file.writeText(yaml.trimIndent())
    return "file:${file.absolutePathString()}"
}

/** The same as [definitionsFile], as the `enact.definitions` property of a context runner. */
fun definitions(yaml: String): String = "enact.definitions=${definitionsFile(yaml)}"
