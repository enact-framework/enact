package io.enact.demo.order

import io.enact.core.annotation.Step
import org.springframework.stereotype.Component
import java.util.UUID

@Step("generateRandomUUID")
@Component
class RandomGeneration : () -> RandomGeneration.Response {
    override fun invoke(): Response = Response(UUID.randomUUID())

    data class Response(
        val id: UUID,
    )
}
