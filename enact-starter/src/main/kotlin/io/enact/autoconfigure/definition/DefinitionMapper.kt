package io.enact.autoconfigure.definition

import org.springframework.format.annotation.DurationFormat
import org.springframework.format.datetime.standard.DurationFormatterUtils
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.module.SimpleModule
import tools.jackson.dataformat.yaml.YAMLMapper
import tools.jackson.module.kotlin.KotlinModule
import java.time.Duration

/**
 * Reads definition files: kebab-case keys, Kotlin defaults honoured, and an unknown key rejected rather than
 * ignored, so a typo fails the application instead of quietly doing nothing.
 */
internal fun definitionMapper(): YAMLMapper =
    YAMLMapper
        .builder()
        .addModule(KotlinModule.Builder().build())
        .addModule(SimpleModule().addDeserializer(Duration::class.java, SpringDurationDeserializer))
        .propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()

/** Accepts the durations Spring Boot accepts elsewhere in the configuration, e.g. `100ms`, `2s`, `PT1M`. */
private object SpringDurationDeserializer : ValueDeserializer<Duration>() {
    override fun deserialize(
        parser: JsonParser,
        context: DeserializationContext,
    ): Duration = DurationFormatterUtils.detectAndParse(parser.string, DurationFormat.Unit.MILLIS)
}
