# CLAUDE.md — Copybara .NET Port

This file orients Claude (and humans) working in this repository. It describes
what the project is, the state of the C# port, the conventions to follow, and
where things live.

## What is Copybara?

Copybara is a tool, originally built and open-sourced by Google, for
**transforming and moving code between repositories**. A typical use case is
keeping an internal (confidential) repository in sync with a public one: you
declare a *workflow* that reads changes from an *origin*, applies a chain of
*transformations* (move files, replace strings, scrub metadata, etc.), and
writes the result to a *destination*, recording state as a label in the
destination commit message so the process is stateless and reproducible.

Configuration is written in **Starlark** (the Python-like dialect used by
Bazel), in a file conventionally named `copy.bara.sky`. The CLI exposes
subcommands like `migrate`, `info`, `validate`, and `version`.

Key domain concepts:

- **Origin** — where code is read from (git, GitHub, Gerrit, Mercurial, a
  local folder, remote files, …).
- **Destination** — where code is written to (git, GitHub PR, a local folder, …).
- **Workflow** — ties an origin, destination, authoring, and a list of
  transformations together. Runs in a mode (`SQUASH`, `ITERATIVE`, `CHANGE_REQUEST`).
- **Transformation** — a reversible (where possible) operation on the checkout
  (`core.replace`, `core.move`, `core.copy`, metadata scrubbers, patch, …).
- **Revision / Change** — a point-in-time state of an origin (e.g. a git SHA)
  and its metadata (author, message, labels, timestamp).
- **Glob** — include/exclude path matcher used pervasively to select files.
- **Authoring** — how author identity is mapped from origin to destination
  (pass-thru, allowlist, overwrite).
- **Console** — abstraction over terminal I/O (ANSI, log, file, prompts).

## Repository layout

```
/                     Root of the .NET port
├── CLAUDE.md         This file
├── TODO.md           Detailed, living port plan / work breakdown
├── README.md         Upstream Copybara readme (kept as-is)
├── LICENSE           Apache 2.0
├── Copybara.sln      .NET solution
├── src/
│   ├── Copybara.Common/    Guava-like collections, Preconditions, helpers
│   ├── Starlark/           Port of net.starlark.java (Starlark interpreter)
│   ├── Copybara.Core/      Port of com.google.copybara.* (the engine)
│   └── Copybara.Cli/       The .NET tool entry point (command `copybara`)
├── tests/
│   └── Copybara.Tests/     xUnit tests (ports of javatests where practical)
└── java/             The ORIGINAL Java/Bazel implementation (reference only)
    ├── com/google/copybara/...        ~90k LOC — the engine
    ├── third_party/bazel/.../net/starlark  ~31k LOC — Starlark interpreter
    ├── javatests/                     Original test suite
    ├── BUILD, MODULE.bazel, ...       Original Bazel build
    └── docs/, scripts/, ci/, ...
```

**The `java/` directory is the source of truth for behavior.** When porting a
class, open the corresponding Java file under `java/com/google/copybara/…` (or
`java/third_party/bazel/main/java/net/starlark/…` for the interpreter) and
mirror its behavior. Do not delete or modify files under `java/`.

## Port goals

1. **Target .NET 10**, C# latest language version, nullable reference types on.
2. **Ship as a .NET tool** (`dotnet tool install --global Copybara`) that
   exposes a `copybara` command with the same CLI surface as upstream.
3. **Preserve behavior and config compatibility**: existing `copy.bara.sky`
   configs should work. This requires a faithful Starlark interpreter.
4. **Use LibGit2Sharp** (NuGet package id: `LibGit2Sharp`) for git plumbing
   instead of shelling out to `git` where practical; fall back to invoking the
   `git` binary only where LibGit2Sharp lacks a feature (e.g. some fetch/push
   refspec behaviors, credential helpers).
5. **Idiomatic C#**: PascalCase members, properties over getters where natural,
   `async`/`Task` for I/O-bound repo operations, records for value types.
6. **Faithful but not slavish**: keep class/relationship structure recognizable
   against the Java source, but drop Java-isms (checked exceptions, builder
   boilerplate where records/init suffice, Guava types where BCL equivalents fit).

## Java → C# mapping conventions

Follow these consistently so the port stays coherent across contributors/agents.

| Java / library                         | C# / .NET                                                        |
|----------------------------------------|-----------------------------------------------------------------|
| `com.google.copybara.foo`              | namespace `Copybara.Foo`                                         |
| `net.starlark.java.eval`               | namespace `Starlark.Eval` (project `Starlark`)                  |
| `ImmutableList<T>`                     | `ImmutableArray<T>` (return as `IReadOnlyList<T>` in APIs)      |
| `ImmutableMap<K,V>`                    | `ImmutableDictionary<K,V>` / `IReadOnlyDictionary<K,V>`         |
| `ImmutableSet<T>`                      | `ImmutableHashSet<T>` / `IReadOnlySet<T>`                       |
| `ImmutableListMultimap<K,V>`           | `Copybara.Common.ImmutableListMultimap<K,V>` (helper type)      |
| `Optional<T>`                          | nullable (`T?`) for refs; `T?`/`Nullable<T>` for structs        |
| `Preconditions.checkNotNull/State`     | `Copybara.Common.Preconditions` helpers                         |
| Guava `Splitter`/`Joiner`/`CharMatcher`| BCL `string.Split`/`string.Join` + helpers in `Copybara.Common` |
| checked exceptions (`throws`)          | plain exceptions; document in XML `<exception>`; no `throws`     |
| `@StarlarkBuiltin` / `@StarlarkMethod` | `[StarlarkBuiltin]` / `[StarlarkMethod]` attributes             |
| `StarlarkValue`                        | `IStarlarkValue` marker interface                               |
| JGit                                   | `LibGit2Sharp` (+ `git` binary fallback via a `GitEnv` runner)  |
| JCommander (`@Parameter`)              | custom lightweight arg parser in `Copybara.Cli` (see TODO)      |
| Flogger `FluentLogger`                 | `Microsoft.Extensions.Logging.ILogger`                          |
| gson                                   | `System.Text.Json`                                              |
| re2j                                   | .NET `System.Text.RegularExpressions` (note: not RE2 semantics) |
| `java.nio.file.Path`                   | `string` paths + `System.IO`; a `CheckoutPath` domain type      |
| JUnit + Truth                          | xUnit + FluentAssertions                                        |

Notes / gotchas:

- **Regex: use the native .NET engine.** Upstream uses re2j, but this port uses
  `System.Text.RegularExpressions` — an accepted deviation. The vast majority of
  Copybara patterns behave identically; note any observed divergence in TODO.
- **Starlark is the critical path.** Almost every domain object is a
  `StarlarkValue` with `@StarlarkMethod` fields, and the config loader executes
  Starlark. The `Starlark` project must land before most `*Module` classes can
  be meaningfully ported. See TODO for the phased approach.
- **File header:** keep the Apache 2.0 license header on ported files.

## Build / test

```
dotnet build Copybara.sln
dotnet test  Copybara.sln
# Run the tool locally:
dotnet run --project src/Copybara.Cli -- migrate copy.bara.sky
# Pack as a global tool:
dotnet pack src/Copybara.Cli -c Release
```

## Status

The port is in progress. **TODO.md is the living work breakdown** — consult it
for what is done, in progress, and pending, and update it as work lands.
</content>
</invoke>
