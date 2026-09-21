# Changelog

## 0.0.1-alpha (2026-09-21)


### ⚠ BREAKING CHANGES

* `MethodAdapter` no longer takes `inputClass` and `outputClass`; it exposes `inputType` and `outputType` as `ResolvableType`.
* **starter:** use cases are keyed by name under `enact.use-cases`, and `name` is no longer a property of a use case definition.

### Features

* **core:** add @StepDefinition stereotype for step classes ([#4](https://github.com/enact-framework/enact/issues/4)) ([af09b2e](https://github.com/enact-framework/enact/commit/af09b2eae971df1a0491400a9bb0573dcce97f34))
* **core:** add per-use-case step settings with retry and cache ([5ee4fa8](https://github.com/enact-framework/enact/commit/5ee4fa84858dd5a535209657daf4ee39b703bcb2))
* **core:** open the trigger SPI to custom entrypoints ([40b9e59](https://github.com/enact-framework/enact/commit/40b9e5907ba951a233072ad678d417c8acea10a3))
* **core:** record metrics and traces for use cases and steps ([#5](https://github.com/enact-framework/enact/issues/5)) ([eabd239](https://github.com/enact-framework/enact/commit/eabd2392cb86c90b6d2170074e79c4689acf54c0))
* define use cases as a typed graph ([#8](https://github.com/enact-framework/enact/issues/8)) ([437eba8](https://github.com/enact-framework/enact/commit/437eba83d16849351cfdb4cf856924526fae086b))
* **demo:** add order demo application ([183bc61](https://github.com/enact-framework/enact/commit/183bc61eda0a8a5d25fe2d5fa158e2cfe70a6174))
* implemented core module to load @Steps at method and class level ([7cd7efa](https://github.com/enact-framework/enact/commit/7cd7efa3d8a3af0d31202357bf3de94f3bee12eb))
* **starter:** define use cases in files, keyed by name ([#6](https://github.com/enact-framework/enact/issues/6)) ([b44a6d8](https://github.com/enact-framework/enact/commit/b44a6d8379a52e2930f70e7a29ee2ca4f589631a))
* **starter:** load use cases from yaml as injectable beans ([de7eaec](https://github.com/enact-framework/enact/commit/de7eaecc6ae14d1cd78ce6eed625d56bec0fcee0))
* support cache settings on @Step ([9cc738c](https://github.com/enact-framework/enact/commit/9cc738cc04d24670adb8d85a220aa53cc048e397))
* **web:** bind headers, query parameters and path variables to use case input ([#2](https://github.com/enact-framework/enact/issues/2)) ([15e5633](https://github.com/enact-framework/enact/commit/15e5633f0bf69be94bea7396743ba734b9e167e7))
* **web:** expose use cases as http endpoints ([7854857](https://github.com/enact-framework/enact/commit/785485750ac8b412b3602c125f28dee71bf2c9bd))
* **web:** group use cases to share REST filters ([#3](https://github.com/enact-framework/enact/issues/3)) ([63b16a0](https://github.com/enact-framework/enact/commit/63b16a0957ed2afb14f4c554f0b4ca5a677a4713))
