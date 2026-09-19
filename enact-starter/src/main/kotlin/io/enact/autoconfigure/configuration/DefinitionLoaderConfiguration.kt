package io.enact.autoconfigure.configuration

import io.enact.autoconfigure.properties.EnactProperties
import io.enact.core.step.Step
import io.enact.core.step.StepRegistrar
import io.enact.core.usecase.RuntimeUseCaseContainer
import io.enact.core.usecase.UseCase
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.BeanFactoryAware
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.getBean
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

    override fun postProcessBeanDefinitionRegistry(registry: BeanDefinitionRegistry) {
        val properties =
            Binder
                .get(environment)
                .bind("enact", EnactProperties::class.java)
                .orElse(null) ?: return

        properties.useCases.forEach { useCaseDefinition ->
            val name = useCaseDefinition.name
            val group = useCaseDefinition.group
            require(group == null || group in properties.groups) {
                "Use case '$name' references unknown group '$group'. Known: ${properties.groups.keys}"
            }
            val beanDefinition =
                RootBeanDefinition().apply {
                    setBeanClass(UseCase::class.java)
                    setDependsOn("stepDiscoveryBeanPostProcessor")

                    instanceSupplier =
                        Supplier<UseCase<*, *>> {
                            initializeStepBeans()
                            createUseCase(name, useCaseDefinition)
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

    private fun createUseCase(
        name: String,
        definition: EnactProperties.UseCaseDefinition,
    ): RuntimeUseCaseContainer<*, *> {
        val stepRegistrar = beanFactory.getBean<StepRegistrar>()
        val steps = definition.steps.map { stepRegistrar.getStep(it.step) }
        val stepSettings = definition.steps.zip(steps) { ref, step -> ref.settings?.orElse(step.settings) ?: step.settings }

        return RuntimeUseCaseContainer<Any, Any>(
            name,
            definition.description ?: "<no description>",
            steps,
            stepSettings,
            beanFactory.getBeanProvider(CacheManager::class.java).ifAvailable,
        )
    }
}
