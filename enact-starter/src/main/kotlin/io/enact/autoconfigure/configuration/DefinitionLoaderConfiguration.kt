package io.enact.autoconfigure.configuration

import io.enact.autoconfigure.definition.DefinitionReader
import io.enact.autoconfigure.definition.Definitions
import io.enact.autoconfigure.definition.UseCaseDefinition
import io.enact.autoconfigure.definition.bindings
import io.enact.autoconfigure.definition.definitionMapper
import io.enact.autoconfigure.definition.fallback
import io.enact.autoconfigure.properties.EnactProperties
import io.enact.core.observation.EnactObservations
import io.enact.core.observation.StepObservationConvention
import io.enact.core.observation.UseCaseObservationConvention
import io.enact.core.step.Step
import io.enact.core.step.StepRegistrar
import io.enact.core.usecase.RuntimeUseCaseContainer
import io.enact.core.usecase.StepNode
import io.enact.core.usecase.UseCase
import io.micrometer.observation.ObservationRegistry
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.BeanFactoryAware
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.getBean
import org.springframework.beans.factory.getBeanProvider
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.cache.CacheManager
import org.springframework.context.EnvironmentAware
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.core.env.Environment
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.util.ClassUtils
import java.util.function.Supplier
import io.enact.core.annotation.Step as StepAnnotation

@Configuration(proxyBeanMethods = false)
open class DefinitionLoaderConfiguration :
    BeanDefinitionRegistryPostProcessor,
    BeanFactoryAware,
    EnvironmentAware {
    private lateinit var beanFactory: ConfigurableListableBeanFactory
    private lateinit var environment: Environment
    private var stepBeansInitialized = false
    private val mapper = definitionMapper()

    override fun postProcessBeanDefinitionRegistry(registry: BeanDefinitionRegistry) {
        val properties =
            Binder
                .get(environment)
                .bind("enact", EnactProperties::class.java)
                .orElseGet(::EnactProperties)

        val resolver = PathMatchingResourcePatternResolver(beanFactory.beanClassLoader)
        val definitions = DefinitionReader(resolver).read(properties.definitions)
        registry.registerBeanDefinition(
            DEFINITIONS_BEAN,
            RootBeanDefinition(Definitions::class.java) { definitions },
        )

        definitions.useCases.forEach { (name, useCaseDefinition) ->
            val group = useCaseDefinition.group
            require(group == null || group in definitions.groups) {
                "Use case '$name'${definitions.sourceOf(name)} references unknown group '$group'. " +
                    "Known: ${definitions.groups.keys}"
            }
            val observed = observabilityEnabled(properties, definitions, useCaseDefinition)
            val beanDefinition =
                RootBeanDefinition().apply {
                    setBeanClass(UseCase::class.java)
                    setDependsOn("stepDiscoveryBeanPostProcessor")

                    instanceSupplier =
                        Supplier<UseCase<*, *>> {
                            initializeStepBeans()
                            createUseCase(name, useCaseDefinition, observed)
                        }
                }

            registry.registerBeanDefinition(name, beanDefinition)
        }
    }

    override fun postProcessBeanFactory(beanFactory: ConfigurableListableBeanFactory) {
        if (beanFactory is DefaultListableBeanFactory) {
            val existing = beanFactory.autowireCandidateResolver
            beanFactory.autowireCandidateResolver = UseCaseAutowireCandidateResolver(existing, beanFactory)
        }
    }

    override fun setEnvironment(environment: Environment) {
        this.environment = environment
    }

    override fun setBeanFactory(beanFactory: BeanFactory) {
        this.beanFactory = beanFactory as ConfigurableListableBeanFactory
    }

    /**
     * Steps are registered by [io.enact.core.configuration.StepDiscoveryBeanPostProcessor] when their
     * declaring bean is initialized. A use case may be created earlier (e.g. when injected into a bean
     * that happens to be created first), so make sure every bean declaring steps is initialized before
     * the first use case is built.
     */
    private fun initializeStepBeans() {
        if (stepBeansInitialized) return
        stepBeansInitialized = true

        beanFactory.beanDefinitionNames
            .filter { name ->
                !beanFactory.getBeanDefinition(name).isAbstract &&
                    !beanFactory.isCurrentlyInCreation(name) &&
                    beanFactory.getType(name, false)?.let(::declaresSteps) == true
            }.forEach { beanFactory.getBean(it) }
    }

    private fun declaresSteps(type: Class<*>): Boolean {
        val userClass = ClassUtils.getUserClass(type)
        return Step::class.java.isAssignableFrom(userClass) ||
            AnnotationUtils.findAnnotation(userClass, StepAnnotation::class.java) != null ||
            userClass.declaredMethods.any { AnnotationUtils.findAnnotation(it, StepAnnotation::class.java) != null }
    }

    /** A use case falls back to its group, a group to `enact.observability`, which defaults to enabled. */
    private fun observabilityEnabled(
        properties: EnactProperties,
        definitions: Definitions,
        definition: UseCaseDefinition,
    ): Boolean =
        definition.observability?.enabled
            ?: definitions.groups[definition.group ?: Definitions.DEFAULT_GROUP]?.observability?.enabled
            ?: properties.observability.enabled
            ?: true

    /**
     * Observations go into the application's [ObservationRegistry], which Spring Boot publishes when Actuator
     * is on the classpath. Without Actuator, or when observability is turned off, the use case gets
     * [ObservationRegistry.NOOP] and records nothing.
     */
    private fun observations(enabled: Boolean): EnactObservations {
        if (!enabled) return EnactObservations.NONE

        val registry = beanFactory.getBeanProvider(ObservationRegistry::class.java).ifAvailable ?: return EnactObservations.NONE
        return EnactObservations(
            registry,
            beanFactory.getBeanProvider<UseCaseObservationConvention>().ifAvailable,
            beanFactory.getBeanProvider<StepObservationConvention>().ifAvailable,
        )
    }

    private fun createUseCase(
        name: String,
        definition: UseCaseDefinition,
        observed: Boolean,
    ): RuntimeUseCaseContainer<*, *> {
        val stepRegistrar = beanFactory.getBean<StepRegistrar>()
        val nodes =
            definition.steps.map { reference ->
                val step = stepRegistrar.getStep(reference.step)
                StepNode(
                    id = reference.id ?: reference.step,
                    step = step,
                    bindings = reference.bindings(name, step),
                    condition = reference.condition,
                    fallback = reference.fallback(name, step, mapper),
                    side = reference.side,
                    settings = reference.settings?.orElse(step.settings) ?: step.settings,
                )
            }

        return RuntimeUseCaseContainer<Any, Any>(
            name,
            definition.description ?: "<no description>",
            nodes,
            definition.output,
            beanFactory.getBeanProvider<CacheManager>().ifAvailable,
            definition.group,
            observations(observed),
            definition.concurrent,
            beanFactory.getBeanProvider<AsyncTaskExecutor>().ifAvailable,
        )
    }

    private companion object {
        const val DEFINITIONS_BEAN = "enactDefinitions"
    }
}
