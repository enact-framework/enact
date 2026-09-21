# Custom trigger

A trigger exposes use cases through a transport. `enact-starter-web` implements the `rest` trigger with the same
SPI you can implement: a new trigger type, such as a message queue or a scheduler, needs no change to Enact.

## The contract

`TriggerHandler<D>` is the only type to implement, one per trigger type, published as a bean. `D` is the class
the trigger configuration binds to.

| Member | |
|---|---|
| `triggerType` | The key under `trigger:` that selects this handler, for example `queue`. Lowercase, unique across handlers. |
| `definitionType` | The `Class` of `D`, bound from the YAML under `trigger.<triggerType>`. |
| `register(registration)` | Called once per use case declaring this trigger. Validate here and throw to fail startup. |
| `start()` | Called once, after every use case is registered. Open routes, consumers or connections. Optional. |
| `stop()` | Called on application shutdown. Close what `start()` opened. Optional. |

It receives a `TriggerRegistration<D>` per use case, with the `useCaseName`, the `useCase` to execute, the bound
`definition` and the `groupFilters` of the use case's group.

## 1. Define the configuration

The definition class lives in your starter, next to the handler. Spring Boot binds it, so a Kotlin data class, a
Java record or a bean all work. Give every property a default, so the YAML only carries what differs.

=== "Kotlin"

    ```kotlin
    data class QueueTriggerDefinition(
        val name: String = "",
        val batchSize: Int = 1,
    )
    ```

=== "Java"

    ```java
    public record QueueTriggerDefinition(String name, int batchSize) {
        public QueueTriggerDefinition {
            batchSize = batchSize == 0 ? 1 : batchSize;
        }
    }
    ```

Relaxed binding applies, so `batch-size: 10` in YAML fills `batchSize`.

## 2. Implement the handler

`register` runs at startup, before the first message is consumed. Validate the definition there: a mistake in the
YAML then stops the application from starting.

=== "Kotlin"

    ```kotlin
    class QueueTriggerHandler(
        private val client: QueueClient,
    ) : TriggerHandler<QueueTriggerDefinition> {
        override val triggerType = "queue"
        override val definitionType = QueueTriggerDefinition::class.java

        private val listeners = mutableListOf<QueueListener>()

        override fun register(registration: TriggerRegistration<QueueTriggerDefinition>) {
            val definition = registration.definition
            require(definition.name.isNotBlank()) { // (1)!
                "Missing queue name for use case '${registration.useCaseName}'"
            }

            val useCase = registration.useCase
            listeners += QueueListener(client, definition) { message ->
                useCase.execute(message.toInput(useCase.inputType)) // (2)!
            }
        }

        override fun start() = listeners.forEach { it.start() } // (3)!

        override fun stop() = listeners.forEach { it.stop() }
    }
    ```

    1.  Startup is the right place to reject an invalid definition.
    2.  `useCase.inputType` is the type the first step takes. Turning a message into it is up to the trigger.
    3.  Registration follows the order of the use cases in the YAML.

=== "Java"

    ```java
    public class QueueTriggerHandler implements TriggerHandler<QueueTriggerDefinition> {
        private final QueueClient client;
        private final List<QueueListener> listeners = new ArrayList<>();

        public QueueTriggerHandler(QueueClient client) {
            this.client = client;
        }

        @Override
        public String getTriggerType() {
            return "queue";
        }

        @Override
        public Class<QueueTriggerDefinition> getDefinitionType() {
            return QueueTriggerDefinition.class;
        }

        @Override
        public void register(TriggerRegistration<QueueTriggerDefinition> registration) {
            QueueTriggerDefinition definition = registration.getDefinition();
            Assert.hasText(definition.name(), "Missing queue name for use case " + registration.getUseCaseName());

            UseCase<Object, Object> useCase = registration.getUseCase();
            listeners.add(new QueueListener(client, definition,
                message -> useCase.execute(message.toInput(useCase.getInputType()))));
        }

        @Override
        public void start() {
            listeners.forEach(QueueListener::start);
        }

        @Override
        public void stop() {
            listeners.forEach(QueueListener::stop);
        }
    }
    ```

## 3. Publish the handler

Any `TriggerHandler` bean is picked up. In a starter, declare it in an auto-configuration:

```kotlin
@AutoConfiguration
@ConditionalOnClass(QueueClient::class)
@ConditionalOnBooleanProperty("enact.enabled", havingValue = true, matchIfMissing = true)
class EnactQueueTriggerAutoConfiguration {
    @Bean
    fun queueTriggerHandler(client: QueueClient) = QueueTriggerHandler(client)
}
```

Register it in `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

## Editor support

Enact declares the trigger types it ships in `TriggerProperties`, so an IDE completes and validates
`trigger.rest.*` against `RestTriggerDefinition`. Nothing reads that class at runtime, because trigger types are
read from the configuration itself. Your trigger type works without an entry there.

An IDE resolves nested configuration by walking Java types, and it does not know your definition class. It
reports your trigger type as an unresolved property, although the application starts and runs:

```
Cannot resolve property 'queue' in io.enact.core.trigger.TriggerProperties
```

Suppress the inspection, or, if Enact should ship the trigger, add it to `TriggerProperties` in a pull request.
In a definition file an IDE says nothing at all, since definition files are not configuration properties: they
are read by Enact itself.

## Lifecycle

| When | What happens |
|---|---|
| Use case beans are created | Steps are resolved and validated; the trigger is not involved yet. |
| All singletons are instantiated | Each use case with a trigger is bound and handed to its handler's `register`. |
| Right after | `start()` is called once per handler that registered something, in registration order. |
| Application shutdown | `stop()` is called, in reverse order. Handlers that registered nothing are skipped. |

An exception from `register` or `start` stops the application from starting.

## Group filters

A group is not tied to one trigger type: a group is a list of bean names, and each trigger resolves them
to its own kind of filter. REST reads them as `HandlerFilterFunction` beans, a queue trigger can read them as its
own interceptor type. They arrive on the registration, outermost first, with the `default` group applied to use
cases without a group:

```kotlin
val interceptors = registration.groupFilters.map { name ->
    beanFactory.getBean(name, QueueInterceptor::class.java)
}
```

If your definition has its own `filters`, apply them after the group's, as the REST trigger does.

## Using the trigger

```yaml title="src/main/resources/enact/orders.yaml"
use-cases:
  handleOrderPlaced:
    trigger:
      queue: # (1)!
        name: orders
        batch-size: 10
    steps:
      - step: handleOrderPlaced
```

1.  Matches `triggerType`. A use case declares exactly one trigger. An unknown type fails startup and lists the
    types that are available.

## Checklist

- A definition class Spring Boot can bind, with a default for every property.
- A `TriggerHandler` bean with a unique, lowercase `triggerType`.
- Validation in `register`, resources in `start`, cleanup in `stop`.
- Group filters applied, if filtering makes sense for the transport.
- A test that starts a context and checks the definition binds as intended.
