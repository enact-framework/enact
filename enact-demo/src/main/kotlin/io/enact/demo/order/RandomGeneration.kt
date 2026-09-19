package io.enact.demo.order

import io.enact.core.annotation.Step
import io.enact.core.annotation.StepDefinition
import java.util.UUID

@Step("generateRandomUUID")
@StepDefinition
class RandomGeneration : () -> RandomGeneration.Response {
    override fun invoke(): Response = Response(UUID.randomUUID())

    data class Response(
        val id: UUID,
    )
}
