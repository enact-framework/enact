# Roadmap

## MVP (0.0.1-alpha)
* Allow simple use-case definition
* Cleanup the project
* Do self review for all the files
* Allow use-case injection
* register a domain
* deploy documentation
* define the license
* step settings: retry + cache (cache storage key).
* HTTP entrypoint
* Metrics and traces (Micrometer observations)
* Use case definition files: several files, custom locations
* Use cases as a typed graph: `in` bindings, step ids, explicit `output`
* Conditional steps: `when` + `else`
* `concurrent` use cases, which the graph runs level by level (this is what `fork` + `join` was for)
* `side`, to run a step aside

## V1
* New entrypoint types
* Actuator endpoint
* SQS Entrypoint
* Kafka entrypoint
* Custom entrypoint definition

## V2
* Evaluate Types at compile time
* Create IDEA Plugin
* gRPC entrypoint