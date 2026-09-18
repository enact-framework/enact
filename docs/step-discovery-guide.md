# Step Discovery & Proxy Registration — Implementation Guide

## Context

Enact needs to automatically discover Step implementations at Spring startup and register them as beans so YAML-defined use cases can resolve step references by name. Two discovery paths exist:

1. **Direct implementation**: Classes implementing the `Step` sealed interface — just collect them
2. **Method-level `@Step`**: Service classes with `@Step`-annotated methods — wrap each method in a `Step` adapter and register as a new bean

---

## Recommended Architecture

**`BeanPostProcessor`** + **`ConfigurableListableBeanFactory.registerSingleton()`**

This is the most Spring-idiomatic approach. The post-processor runs after each bean is initialized, so you have the actual instance and can inspect methods via reflection. Adapter beans are registered as singletons — they're simple POJOs with no lifecycle needs.

### Why This Over Other Extension Points

| Extension Point | Problem |
|---|---|
| `BeanDefinitionRegistryPostProcessor` | Runs before instantiation — you'd need classpath scanning and class-loading to find `@Step` methods, duplicating Spring's component scan |
| `ImportBeanDefinitionRegistrar` | Same timing issue — too early, no bean instances yet |
| `SmartInitializingSingleton` | Too late — other listeners may have already tried to resolve step beans |
| `ClassPathBeanDefinitionScanner` | Useful for custom component scanning, but overkill here since beans are already discovered by Spring's standard `@Service`/`@Component` scan |

---

## Component Design

### 1. `MethodStepAdapter` (enact-core)

`enact-core/src/main/kotlin/io/enact/core/usecase/MethodStepAdapter.kt`

Wraps a `@Step`-annotated method as a `Step.InOutStep`. Key points:

- Implements `Step.InOutStep<Any, Any>` (raw types)
- Stores **actual** `inputType: Class<*>` and `outputType: Class<*>` extracted from the method signature
- `execute()` delegates to `method.invoke(bean, input)` — plain reflection, no CGLIB/ByteBuddy needed
- `name` = method name (matches YAML reference)
- `description` = `@Step.title` annotation value

**Why raw types + stored metadata?** A generic `MethodStepAdapter<Input, Output>` won't work with `GenericTypeResolver.resolveTypeArguments()` — it can't resolve type variables, only concrete types baked into the class hierarchy. Storing the types explicitly and checking for the adapter in `UseCaseContainer.resolveTypes()` is the simplest fix.

### 2. Modify `UseCaseContainer.resolveTypes()` (enact-core)

`enact-core/src/main/kotlin/io/enact/core/usecase/UseCaseContainer.kt`

Add an `is MethodStepAdapter` check to short-circuit `GenericTypeResolver`:

```kotlin
private fun resolveTypes(instance: Any, clazz: KClass<*>, genericType: KClass<*>): InOutTypes {
    if (instance is MethodStepAdapter) {
        return InOutTypes(instance.inputType, instance.outputType)
    }
    // existing GenericTypeResolver logic...
}
```

Update call sites in `validateFirstStepInput`, `validateLastStepOutput`, `validateStepChain` to pass the step instance alongside the KClass.

### 3. `StepRegistry` (enact-core)

`enact-core/src/main/kotlin/io/enact/core/usecase/StepRegistry.kt`

Simple `Map<String, Step<*, *>>` wrapper. Throws on duplicate names. Used by YAML resolution to look up steps by name. Plain class — not a Spring bean in core, instantiated by the auto-configuration.

### 4. `StepDiscoveryBeanPostProcessor` (enact-starter)

`enact-starter/src/main/kotlin/io/enact/starter/StepDiscoveryBeanPostProcessor.kt`

Implements `BeanPostProcessor`. In `postProcessAfterInitialization()`:

**Case 1 — Direct `Step` implementor:**
```
if (bean is Step<*, *>) → registry.register(bean)
```

**Case 2 — `@Step` method:**
```
scan targetClass.declaredMethods for @Step annotation
for each annotated method:
  validate: exactly 1 parameter, non-void return
  extract inputType, outputType from method signature
  create MethodStepAdapter(name=method.name, bean=bean, method=method, ...)
  registry.register(adapter)
  beanFactory.registerSingleton(method.name, adapter)
```

**Handling Spring AOP proxies:** When a `@Service` is wrapped by CGLIB (e.g. `@Transactional`), annotations may not be on the proxy class. Use `AopUtils.getTargetClass(bean)` to get the original class for method scanning, but pass the **proxy** `bean` to the adapter so AOP interception still fires:

```kotlin
val targetClass = AopUtils.getTargetClass(bean)
// scan targetClass for @Step methods
// but create adapter with: bean = bean (the proxy), method = proxyMethod
val proxyMethod = bean::class.java.getMethod(method.name, *method.parameterTypes)
```

`AopUtils` is in `spring-aop`, transitively available via `spring-boot-starter-web`.

### 5. `EnactAutoConfiguration` (enact-starter)

`enact-starter/src/main/kotlin/io/enact/starter/EnactAutoConfiguration.kt`

```kotlin
@AutoConfiguration
class EnactAutoConfiguration {
    @Bean fun stepRegistry(): StepRegistry
    @Bean fun stepDiscoveryBeanPostProcessor(registry, beanFactory): StepDiscoveryBeanPostProcessor
}
```

Register via Spring Boot 3.x mechanism:

`enact-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
```
io.enact.starter.EnactAutoConfiguration
```

---

## Complete Startup Flow

```
1. Spring Boot starts → component scanning finds @Service beans
   (OrderValidationService, OrderPersistingService, etc.)

2. EnactAutoConfiguration loaded via AutoConfiguration.imports
   → StepRegistry bean created
   → StepDiscoveryBeanPostProcessor bean created

3. For each bean, postProcessAfterInitialization() fires:

   OrderValidationService:
     → finds @Step on validateOrderCreation(OrderRequest): OrderRequest
     → creates MethodStepAdapter("validateOrderCreation", OrderRequest, OrderRequest)
     → registry.register(adapter)
     → beanFactory.registerSingleton("validateOrderCreation", adapter)

   OrderPersistingService:
     → finds @Step on saveOrder(OrderRequest): OrderEntity
     → creates MethodStepAdapter("saveOrder", OrderRequest, OrderEntity)
     → registers...

4. After all beans processed, StepRegistry contains:
   "validateOrderCreation" → MethodStepAdapter(OrderRequest → OrderRequest)
   "saveOrder"             → MethodStepAdapter(OrderRequest → OrderEntity)
   "mapOrderResponse"      → MethodStepAdapter(OrderEntity → OrderResponse)

5. (Future) YAML parser resolves step names against StepRegistry,
   builds UseCaseContainer instances, validates type chain.
```

---

## Edge Cases

| Case | Handling |
|---|---|
| **CGLIB proxy hides annotations** | Use `AopUtils.getTargetClass(bean)` for scanning, proxy instance for invocation |
| **Duplicate step names** | `StepRegistry.register()` throws on conflict — fail-fast at startup |
| **Multiple @Step methods on one bean** | Each becomes a separate adapter bean — all registered independently |
| **Zero/multiple params** | Validate at discovery time, throw with clear error message |
| **Void return** | For now: reject with error. Later: support via `InStep` adapter variant |
| **Kotlin `Unit` return** | `method.returnType` returns `void` for Kotlin Unit — handle same as void |
| **Bean not a singleton** | `registerSingleton()` always registers a singleton — if the host bean is prototype-scoped, the adapter will still delegate to the same instance captured at discovery time. Consider warning for non-singleton host beans |

---

## Files Summary

| File | Action | Module |
|---|---|---|
| `core/usecase/MethodStepAdapter.kt` | Create | enact-core |
| `core/usecase/StepRegistry.kt` | Create | enact-core |
| `core/usecase/UseCaseContainer.kt` | Modify `resolveTypes()` | enact-core |
| `starter/EnactAutoConfiguration.kt` | Create | enact-starter |
| `starter/StepDiscoveryBeanPostProcessor.kt` | Create | enact-starter |
| `starter/resources/META-INF/spring/...AutoConfiguration.imports` | Create | enact-starter |

---

## Testing

**enact-core unit tests:**
- `MethodStepAdapterTest` — `execute()` delegates correctly, types are stored
- `StepRegistryTest` — register/get, duplicate rejection
- Extend `UseCaseContainerTest` — adapter-based steps pass type chain validation

**enact-starter integration tests:**
- `@SpringBootTest` with test `@Service` classes containing `@Step` methods
- Assert steps in registry, retrievable from `ApplicationContext` by method name
- Assert invalid `@Step` methods (wrong param count, void return) fail startup
- Assert direct `Step` implementors are discovered
