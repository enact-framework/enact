package io.enact.core.configuration.stepdefinitions

import io.enact.core.annotation.Step
import io.enact.core.annotation.StepDefinition

@StepDefinition
class ScannedSteps {
    @Step
    fun scannedStep(input: String): String = input
}

@Step("scanned-functional")
@StepDefinition("functionalSteps")
class ScannedFunctionalStep : (String) -> String {
    override fun invoke(input: String): String = input
}
