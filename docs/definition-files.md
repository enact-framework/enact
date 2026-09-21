# Definition files

Use cases live in their own YAML files, one per domain. Put them in `src/main/resources/enact/` and Enact reads
them when the application starts:

```yaml title="src/main/resources/enact/orders.yaml"
use-cases:
  createOrder: # (1)!
    description: Validates and stores a new order
    trigger:
      rest:
        method: POST
        path: /api/v1/orders
        status: 201
    steps:
      - step: validateOrder
      - step: saveOrder
      - step: mapOrderResponse
```

1.  The key is the name of the use case, and of the Spring bean Enact registers for it. The file starts at
    `use-cases`, without the `enact:` prefix of `application.yaml`.

Nothing else is needed: `classpath:enact/` is where Enact looks by default. `application.yaml` keeps the
settings of the application, and the use cases stay next to the domain they describe.

!!! warning "Use cases cannot be written in `application.yaml`"
    `enact.use-cases` and `enact.groups` are no longer read. A definition file is the one place a use case is
    declared. What remains in `application.yaml` is Enact's own configuration: `enact.enabled`,
    `enact.definitions` and `enact.observability`.

## One file per domain

```text
src/main/resources/enact/
├── orders.yaml
└── users.yaml
```

Enact reads every `.yaml` and `.yml` file of the folder, in file name order. The file a use case is written in
changes nothing about the way it runs; it is a way to keep a domain together.

## Other locations

`enact.definitions` takes files, folders and [Ant patterns](https://docs.spring.io/spring-framework/reference/core/resources.html#resources-app-ctx-wildcards-in-resource-paths):

```yaml title="application.yaml"
enact:
  definitions:
    - classpath:enact/                # every YAML file of a folder
    - classpath:orders/use-cases.yaml # a single file
    - classpath*:enact/**/*.yaml      # also the files of the other jars
    - file:./config/use-cases/        # outside the application
```

Files are read in the order they are listed. Setting `enact.definitions` replaces the default location, so add
`classpath:enact/` to the list to keep reading it.

A location that matches no file stops the application, which catches a typo in a path. Write `optional:` in
front of a location that may be missing:

```yaml title="application.yaml"
enact:
  definitions:
    - classpath:enact/
    - optional:file:./config/use-cases/
```

The default location is optional, so an application without definition files starts as usual.

## Groups next to their use cases

A [group](http-trigger.md#filters-and-groups) shared by the whole application belongs in a file of its own. A
group that one domain uses can be written in that domain's file instead:

```yaml title="src/main/resources/enact/orders.yaml"
use-cases:
  updateOrder:
    group: authenticated
    steps:
      - step: updateOrder

groups:
  authenticated:
    filters: [requestLog, requireUser]
```

A use case name is a bean name and a group name is not, so the two never collide: a use case and a group may
share a name.

## How they are read

Definition files are parsed with Jackson, not bound as Spring configuration properties. That is what makes a
name mean exactly what the file says: a configuration property name is lower-cased, while a use case name, a
step id, a `bind` entry and a parameter name in `in` are all case-sensitive.

Keys are kebab-case, as elsewhere in Spring Boot (`use-cases`, `max-retries`), and durations take the forms
Spring Boot accepts (`100ms`, `2s`, `PT1M`). An unknown key is rejected rather than ignored, so a typo fails
the application instead of quietly doing nothing.

## What Enact checks

At startup, before anything runs:

- a file declares `use-cases` and `groups`, and nothing else,
- every key below them is one a definition knows,
- a use case name is declared once across every file, and a group name likewise, and
- every location matches at least one YAML file, unless it is `optional:`.

An error names the file it comes from, and a key that could not be read carries the line it is on:

```text
Cannot read use case definitions from class path resource [enact/orders.yaml]:
Unrecognized field "stepz" ... at line 4, column 9.
```

```text
Use case 'createOrder' is declared twice: class path resource [enact/orders.yaml]
and class path resource [enact/legacy.yaml].
```

A file that starts at `enact:` fails the same way, since `enact` is a root key like any other. The error then
says that a definition file declares `use-cases` and `groups` at its root, without the prefix.

Once the files are read, the use cases themselves are checked; see [Validation](use-cases.md#validation).

## Editor support

An IDE completes `enact.*` keys in `application.yaml`, where it reads Spring configuration metadata. A
definition file is a plain YAML file to it, so nothing completes or checks the keys written there unless you
point the editor at Enact's JSON schema:

```
https://enact-framework.github.io/enact/schema/definition-file.schema.json
```

In IntelliJ, add it under *Settings → Languages & Frameworks → Schemas and DTDs → JSON Schema Mappings* for
the `enact/` folder. In VS Code, map it under `yaml.schemas`:

```json title=".vscode/settings.json"
{
  "yaml.schemas": {
    "https://enact-framework.github.io/enact/schema/definition-file.schema.json": "src/main/resources/enact/*.yaml"
  }
}
```

!!! note "The schema checks shape, not meaning"
    It catches a misspelled key, a value of the wrong kind and a missing `step`. Whether a step exists,
    whether the types along an edge fit and whether the graph has exactly one output are questions about your
    code, so Enact answers them at startup. See [Validation](use-cases.md#validation).

!!! note "`spring.config.import` reads Spring configuration, not definition files"
    A definition file starts at `use-cases`, not at `enact:`, so importing one as configuration puts its keys
    in the wrong place. `enact.definitions` is what reads them, and it takes a folder, which
    `spring.config.import` does not.
