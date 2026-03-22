package io.enact.core.configuration

import io.enact.core.step.StepRegistrar
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Role

@Configuration
open class EnactConfiguration {
    companion object {
        @Bean
        @JvmStatic
        @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
        fun stepRegistrar(): StepRegistrar = StepRegistrar()

        @Bean
        @JvmStatic
        fun stepDiscoveryBeanPostProcessor(stepRegistrar: StepRegistrar): StepDiscoveryBeanPostProcessor =
            StepDiscoveryBeanPostProcessor(stepRegistrar)
    }
}
