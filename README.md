<p align="center">
  <img src="assets/icon.png" alt="Copybara logo" width="180" />
</p>

# Copybara for .NET

*A tool for transforming and moving code between repositories — a C# / .NET 10
port of [Google Copybara](https://github.com/google/copybara).*

Copybara transforms and moves source code between repositories. A common use
case is keeping an internal (confidential) repository in sync with a public
one: you declare a *workflow* that reads changes from an **origin**, applies a
chain of **transformations** (move files, replace strings, scrub metadata, …),
and writes the result to a **destination**, recording state in the destination
commit message so the process is stateless and reproducible.

Configuration is written in **Starlark** (the Python-like dialect used by
Bazel) in a file conventionally named `copy.bara.sky`.

```python
core.workflow(
    name = "default",
    origin = git.github_origin(
        url = "https://github.com/google/copybara.git",
        ref = "master",
    ),
    destination = git.destination(
        url = "file:///tmp/foo",
    ),
    # Copy everything but don't remove README_INTERNAL.txt if it exists.
    destination_files = glob(["third_party/copybara/**"], exclude = ["README_INTERNAL.txt"]),
    authoring = authoring.pass_thru("Default email <default@default.com>"),
    transformations = [
        core.replace(
            before = "//third_party/bazel/bashunit",
            after  = "//another/path:bashunit",
        ),
        core.move("foo/bar", "baz/bar"),
    ],
)
```

## About this repository

This repository is a **port of Copybara from Java to C# targeting .NET 10**,
distributed as a **.NET global tool** named `copybara`.

- The original Java/Bazel implementation is preserved, unmodified, under
  [`java/`](java/) and is the reference for behavior. Its original README lives
  at [`java/README.md`](java/README.md).
- The C# port lives under [`src/`](src/) and [`tests/`](tests/).
- [`CLAUDE.md`](CLAUDE.md) documents the architecture, porting conventions, and
  Java→C# mappings. [`TODO.md`](TODO.md) is the living work breakdown and status.

> **Status:** the port is a work in progress. See [`TODO.md`](TODO.md) for what
> is implemented and what remains.

## Layout

```
src/
  Copybara.Common/   Guava-like helpers (Preconditions, ImmutableListMultimap)
  Starlark/          Port of the Starlark interpreter (net.starlark.java)
  Copybara.Core/     The engine (origins, destinations, transformations, git, …)
  Copybara.Cli/      The `copybara` .NET tool entry point
tests/
  Copybara.Tests/    xUnit tests
java/                The original Java/Bazel implementation (reference only)
```

## Building

Requires the **.NET 10 SDK**.

```bash
dotnet build Copybara.slnx
dotnet test  Copybara.slnx
```

## Running

```bash
# From source:
dotnet run --project src/Copybara.Cli -- migrate copy.bara.sky

# Packaged as a global tool:
dotnet pack src/Copybara.Cli -c Release
dotnet tool install --global --add-source src/Copybara.Cli/nupkg Copybara
copybara migrate copy.bara.sky
```

## Notable porting decisions

- **Git** operations use the [`LibGit2Sharp`](https://www.nuget.org/packages/LibGit2Sharp)
  NuGet package, with a fallback to invoking the `git` binary for features it
  does not cover.
- **Regular expressions** use the native .NET regex engine
  (`System.Text.RegularExpressions`) rather than RE2. This is an accepted
  deviation from upstream (which uses re2j); the vast majority of Copybara
  patterns behave identically.
- Starlark configuration semantics are preserved by porting the Bazel Starlark
  interpreter to C# (see `src/Starlark`).

## License

Apache License 2.0 — see [`LICENSE`](LICENSE). Copybara is a trademark of
Google LLC; this is an independent port.
