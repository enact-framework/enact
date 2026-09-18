package io.enact.autoconfigure.configuration

import io.enact.core.usecase.UseCase
import org.springframework.beans.factory.config.BeanDefinitionHolder
import org.springframework.beans.factory.config.DependencyDescriptor
import org.springframework.beans.factory.support.AutowireCandidateResolver
import org.springframework.beans.factory.support.DefaultListableBeanFactory

/**
 * Custom [AutowireCandidateResolver] that enforces generic type matching for [UseCase] injection points.
 *
 * By default, Spring erases generic types on dynamically registered bean definitions, causing
 * injection to fall back to bean name matching only. This resolver intercepts candidate evaluation
 * for `UseCase<Input, Output>` dependencies and compares the injection point's generic type
 * arguments against the candidate bean's [UseCase.inputType] and [UseCase.outputType],
 * rejecting candidates whose types do not match.
 *
 * All non-[UseCase] injection points are delegated to the wrapped resolver unchanged.
 */
class UseCaseAutowireCandidateResolver(
    private val delegate: AutowireCandidateResolver,
    private val beanFactory: DefaultListableBeanFactory,
) : AutowireCandidateResolver by delegate {
    override fun isAutowireCandidate(
        bdHolder: BeanDefinitionHolder,
        descriptor: DependencyDescriptor,
    ): Boolean {
        if (!delegate.isAutowireCandidate(bdHolder, descriptor)) return false

        val generics = descriptor.resolvableType.generics
        if (descriptor.resolvableType.rawClass != UseCase::class.java || generics.size != 2) return true

        val bean = beanFactory.getBean(bdHolder.beanName) as? UseCase<*, *> ?: return true

        return bean.inputType == generics[0].resolve() && bean.outputType == generics[1].resolve()
    }
}
