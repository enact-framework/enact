package io.enact.autoconfigure

import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.io.path.writeText

/**
 * Writes [yaml] as a definition file and returns the `enact.definitions` property pointing at it.
 *
 * Use cases are no longer configuration properties, so a test declares them the way an application does.
 */
fun definitions(yaml: String): String {
    val file = Files.createTempFile("enact-definitions-", ".yaml")
    file.toFile().deleteOnExit()
    file.writeText(yaml.trimIndent())
    return "enact.definitions=file:${file.absolutePathString()}"
}
