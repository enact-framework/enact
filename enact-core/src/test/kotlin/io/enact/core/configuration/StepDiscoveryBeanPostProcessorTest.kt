package io.enact.core.configuration

import io.enact.core.step.MethodAdapter
import io.enact.core.step.Step
import io.enact.core.step.StepRegistrar
import io.enact.core.step.StepSettings
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate
import java.util.function.Supplier
import io.enact.core.annotation.Step as StepAnnotation

@SpringJUnitConfig(EnactConfiguration::class, StepDiscoveryBeanPostProcessorTest.TestConfig::class)
class StepDiscoveryBeanPostProcessorTest {
    @Autowired
    lateinit var stepRegistrar: StepRegistrar

    @Autowired
    lateinit var processor: StepDiscoveryBeanPostProcessor

    @Test
    fun `should create StepRegistrar and StepDiscoveryBeanPostProcessor beans`() {
        assert(::stepRegistrar.isInitialized)
        assert(::processor.isInitialized)
    }

    @Test
    fun `should register bean that implements Step interface`() {
        val step = stepRegistrar.getStep("direct-step")
        assert(step is Step.InOut<*, *>)
        assert(step.name == "direct-step")
    }

    @Test
    fun `should discover methods annotated with @Step`() {
        val step = stepRegistrar.getStep("validate")
        assert(step is MethodAdapter)
        assert(step.name == "validate")
    }

    @Test
    fun `should use method name when @Step name is blank`() {
        val step = stepRegistrar.getStep("process")
        assert(step is MethodAdapter)
    }

    @Test
    fun `should handle method with no parameters as Unit input`() {
        val step = stepRegistrar.getStep("generate") as MethodAdapter
        assert(step.parameters.isEmpty())
        assert(step.outputType.toClass() == String::class.java)
    }

    @Test
    fun `should not register plain beans without Step interface or annotation`() {
        assertThrows<IllegalArgumentException> {
            stepRegistrar.getStep("doSomething")
        }
    }

    @Test
    fun `should resolve typed method for Java Function and not bridge method`() {
        val step = stepRegistrar.getStep("transform") as MethodAdapter
        assert(
            step.parameters
                .single()
                .type
                .toClass() == String::class.java,
        ) {
            "Expected String but got ${step.parameters.single().type.toClass()}"
        }
        assert(step.outputType.toClass() == String::class.java) { "Expected String but got ${step.outputType.toClass()}" }
    }

    @Test
    fun `should resolve typed method for Predicate and not bridge method`() {
        val step = stepRegistrar.getStep("check") as MethodAdapter
        assert(
            step.parameters
                .single()
                .type
                .toClass() == String::class.java,
        ) {
            "Expected String but got ${step.parameters.single().type.toClass()}"
        }
        assert(step.outputType.toClass() == Boolean::class.java) { "Expected Boolean but got ${step.outputType.toClass()}" }
    }

    @Test
    fun `should resolve typed method for Consumer and not bridge method`() {
        val step = stepRegistrar.getStep("consume") as MethodAdapter
        assert(
            step.parameters
                .single()
                .type
                .toClass() == String::class.java,
        ) {
            "Expected String but got ${step.parameters.single().type.toClass()}"
        }
    }

    @Test
    fun `should resolve typed method for Supplier and not bridge method`() {
        val step = stepRegistrar.getStep("supply") as MethodAdapter
        assert(step.parameters.isEmpty()) { "Expected no parameter but got ${step.parameters}" }
        assert(step.outputType.toClass() == String::class.java) { "Expected String but got ${step.outputType.toClass()}" }
    }

    @Test
    fun `should resolve typed method for Kotlin Function1 and not bridge method`() {
        val step = stepRegistrar.getStep("kotlin-transform") as MethodAdapter
        assert(
            step.parameters
                .single()
                .type
                .toClass() == String::class.java,
        ) {
            "Expected String but got ${step.parameters.single().type.toClass()}"
        }
        assert(step.outputType.toClass() == String::class.java) { "Expected String but got ${step.outputType.toClass()}" }
    }

    @Test
    fun `should resolve typed method for Kotlin Function0 and not bridge method`() {
        val step = stepRegistrar.getStep("kotlin-supply") as MethodAdapter
        assert(step.parameters.isEmpty()) { "Expected no parameter but got ${step.parameters}" }
        assert(step.outputType.toClass() == String::class.java) { "Expected String but got ${step.outputType.toClass()}" }
    }

    @Test
    fun `should capture the parameters of a @Step method taking several`() {
        val registrar = StepRegistrar()
        StepDiscoveryBeanPostProcessor(registrar).postProcessAfterInitialization(SeveralParamsService(), "several")

        val step = registrar.getStep("several") as MethodAdapter

        assert(step.parameters.map { it.name } == listOf("a", "b")) { "Got ${step.parameters.map { it.name }}" }
        assert(step.parameters.map { it.type.toClass() } == listOf(String::class.java, Int::class.java))
    }

    @Test
    fun `should invoke a @Step method with one argument per parameter`() {
        val registrar = StepRegistrar()
        StepDiscoveryBeanPostProcessor(registrar).postProcessAfterInitialization(SeveralParamsService(), "several")

        val step = registrar.getStep("several") as MethodAdapter

        assert(step.invoke(listOf("x", 2)) == "x2")
    }

    @Configuration
    open class TestConfig {
        @Bean
        open fun directStep(): Step.InOut<String, String> =
            object : Step.InOut<String, String> {
                override val name = "direct-step"
                override var settings: StepSettings? = null

                override fun execute(input: String): String = input
            }

        @Bean
        open fun annotatedService() = AnnotatedService()

        @Bean
        open fun defaultNameService() = DefaultNameService()

        @Bean
        open fun noParamService() = NoParamService()

        @Bean
        open fun plainService() = PlainService()

        @Bean
        open fun stringTransformer() = StringTransformer()

        @Bean
        open fun stringChecker() = StringChecker()

        @Bean
        open fun stringConsumer() = StringConsumer()

        @Bean
        open fun stringSupplier() = StringSupplier()

        @Bean
        open fun kotlinTransformer() = KotlinTransformer()

        @Bean
        open fun kotlinSupplier() = KotlinSupplier()
    }

    class AnnotatedService {
        @StepAnnotation(name = "validate")
        fun validateOrder(input: String): String = input
    }

    class DefaultNameService {
        @StepAnnotation
        fun process(input: String): String = input
    }

    class NoParamService {
        @StepAnnotation(name = "generate")
        fun generate(): String = "value"
    }

    class PlainService {
        fun doSomething(): String = "nothing"
    }

    class SeveralParamsService {
        @StepAnnotation(name = "several")
        fun several(
            a: String,
            b: Int,
        ): String = "$a$b"
    }

    @StepAnnotation(name = "transform")
    class StringTransformer : Function<String, String> {
        override fun apply(t: String): String = t.uppercase()
    }

    @StepAnnotation(name = "check")
    class StringChecker : Predicate<String> {
        override fun test(t: String): Boolean = t.isNotEmpty()
    }

    @StepAnnotation(name = "consume")
    class StringConsumer : Consumer<String> {
        override fun accept(t: String) { /* no-op */ }
    }

    @StepAnnotation(name = "supply")
    class StringSupplier : Supplier<String> {
        override fun get(): String = "supplied"
    }

    @StepAnnotation(name = "kotlin-transform")
    class KotlinTransformer : (String) -> String {
        override fun invoke(input: String): String = input.lowercase()
    }

    @StepAnnotation(name = "kotlin-supply")
    class KotlinSupplier : () -> String {
        override fun invoke(): String = "kotlin-supplied"
    }
}
