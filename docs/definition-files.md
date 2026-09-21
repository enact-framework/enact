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
      - step: validateOrderCreation
      - step: saveOrder
      - step: mapOrderResponse
```

1.  The key is the name of the use case, and of the Spring bean Enact registers for it. The file starts at
    `use-cases`, without the `enact:` prefix of `application.yaml`.

Nothing else is needed: `classpath:enact/` is where Enact looks by default. `application.yaml` keeps the
settings of the application, and the use cases stay next to the domain they describe.

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

A [group](http-trigger.md#filters-and-groups) shared by the whole application belongs in `application.yaml`. A group that one
domain uses can be written in its file instead:

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

## What Enact checks

At startup, before anything runs:

- a file declares `use-cases` and `groups`, and nothing else,
- a use case name is declared once, in one file or in `application.yaml`,
- a group is declared in one place, either a file or `application.yaml`, and
- every location matches at least one YAML file, unless it is `optional:`.

An error names the file and the line it comes from:

```text
Use case 'createOrder' is declared twice: class path resource [enact/orders.yaml] - 2:3
and class path resource [enact/legacy.yaml] - 7:3.
```

!!! note "Editor support"
    An IDE completes `enact.*` keys in `application.yaml`, where it reads Spring configuration metadata. A
    definition file is a plain YAML file to it, so it neither completes nor checks the keys written there.

## Use cases in application.yaml

Use cases can still be written under `enact.use-cases` in `application.yaml`, which is short enough for a test
or a small application:

```yaml title="application.yaml"
enact:
  use-cases:
    generateId:
      steps:
        - step: generateRandomUUID
```

The files and `application.yaml` are read together. Because use cases are keyed by name, Spring merges them
like any other map, and a name declared in both places is an error rather than one of them quietly winning.

!!! note "`spring.config.import` reads Spring configuration, not definition files"
    A definition file starts at `use-cases`, not at `enact:`, so importing one as configuration puts its keys
    in the wrong place. `enact.definitions` is what reads them, and it takes a folder, which
    `spring.config.import` does not.
