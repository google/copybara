# TODO — Copybara .NET 10 Port

Living work breakdown. Status legend: ✅ done · 🚧 in progress · ⬜ pending ·
🔬 needs investigation.

The port is large (~90k LOC engine + ~31k LOC Starlark interpreter). It is
organized in dependency order: foundation first, then the Starlark interpreter,
then the domain modules that depend on both.

---

## Phase 0 — Project setup

- ✅ Move original Java/Bazel tree into `java/` (reference only).
- ✅ Author `CLAUDE.md` and `TODO.md`.
- 🚧 Create solution + project scaffold:
  - `src/Copybara.Common` — helpers (Preconditions, ImmutableListMultimap, Glob-support types).
  - `src/Starlark` — Starlark interpreter port.
  - `src/Copybara.Core` — engine.
  - `src/Copybara.Cli` — the `copybara` .NET tool (`PackAsTool=true`).
  - `tests/Copybara.Tests` — xUnit.
- 🚧 Wire NuGet deps: `LibGit2Sharp`, `Microsoft.Extensions.Logging`,
  `System.Text.Json`, `System.Collections.Immutable`, xUnit, FluentAssertions.
- ⬜ `.gitignore` for .NET (`bin/`, `obj/`, `*.user`).
- ⬜ CI: `dotnet build` + `dotnet test` GitHub Action.

## Phase 1 — Foundation (`Copybara.Common` + `Copybara.Core` primitives)

No Starlark dependency. Port these first; they unblock everything else.

- 🚧 `Copybara.Common`
  - `Preconditions` (CheckNotNull, CheckArgument, CheckState).
  - `ImmutableListMultimap<K,V>` + builder.
  - String helpers (Splitter/Joiner/CharMatcher equivalents as needed).
- 🚧 `exception/` → `Copybara.Exceptions`
  - `ValidationException` (+ `CheckCondition`), `RepoException`,
    `CannotResolveRevisionException`, `EmptyChangeException`,
    `ChangeRejectedException`, `NonReversibleValidationException`,
    `CommandLineException`, `RedundantChangeException`, `VoidOperationException`,
    `NotADestinationFileException`, `AccessValidationException`,
    `CannotResolveLabel`.
- ⬜ `util/` core (subset, no git):
  - `ExitCode`, `Glob` + `GlobAtom` + `SequenceGlob` + `ReadablePathMatcher`,
    `FileUtil`, `DirFactory`, `CommandOutput(WithStatus)`, `CommandRunner`
    (process runner), `Identity`, `TablePrinter`, `RenameDetector`, `DiffUtil`
    (backed by `git diff`/LibGit2Sharp), `OriginUtil`.
- ⬜ `util/console/` → `Copybara.Util.Console`
  - `Console` interface, `AnsiConsole`, `LogConsole`, `FileConsole`,
    `NoPromptConsole`, `CapturingConsole`, `Consoles`, `ProgressPrefixConsole`.
- ⬜ `revision/` → `Copybara.Revision`: `Revision`, `Change`, `Changes`, `OriginRef`.
- ⬜ `authoring/` → `Copybara.Authoring`: `Author`, `AuthorParser`, `Authoring`,
  `InvalidAuthorException` (note: `Author`/`Authoring` are Starlark values — see Phase 3).
- ⬜ `templatetoken/` → `Copybara.TemplateToken`.
- ⬜ `profiler/` → `Copybara.Profiler`.

## Phase 2 — Starlark interpreter (`src/Starlark`)

Port of `java/third_party/bazel/main/java/net/starlark/java`. This is a
self-contained interpreter and the critical dependency for config loading.
Sub-packages: `annot`, `syntax` (lexer/parser/AST), `eval` (values, evaluator,
builtins), `lib` (json, proto, etc.), `spelling`.

- ⬜ `annot/` — attributes: `[StarlarkBuiltin]`, `[StarlarkMethod]`,
  `[Param]`, `[ParamType]`, `StarlarkDocumentationCategory`, etc.
- ⬜ `syntax/` — `Lexer`, `Parser`, AST node types, `Location`, `FileOptions`,
  `SyntaxError`.
- ⬜ `eval/` — `StarlarkValue`, `Starlark` (entry/helpers), `StarlarkThread`,
  `Module`, `Mutability`, `StarlarkInt/Float/List/Dict/Tuple/Function`,
  `EvalException`, `MethodLibrary`, `StarlarkCallable`, `Structure`,
  `CallUtils`/method-descriptor reflection (map `@StarlarkMethod` → dispatch).
- ⬜ `lib/json` — `Json` module (interop with `System.Text.Json`).
- ⬜ `spelling/` — `SpellChecker`.
- ⬜ Decide reflection strategy: Java uses annotation processing + reflection.
  In C#, use reflection over `[StarlarkMethod]` attributes at startup, cached
  per type. (Source generators are a later optimization.)
- 🔬 Validate with a handful of upstream Starlark eval tests ported to xUnit.

## Phase 3 — Config model & core module (needs Phases 1–2)

- ⬜ `config/` → `Copybara.Config`: `Config`, `ConfigFile`,
  `PathBasedConfigFile`, `MapConfigFile`, `Migration`, `ConfigValidator`,
  `SkylarkParser`, `Config(Loader/Provider)`, `LabelsAwareModule`.
- ⬜ Top-level engine types in `com.google.copybara/`:
  `Option`/`Options`, `GeneralOptions`, `ModuleSet`/`ModuleSupplier`,
  `Core`(`CoreModule`, `CoreGlobal`), `Workflow`, `WorkflowOptions`,
  `WorkflowMode`, `WorkflowRunHelper`, `Migration`, `Origin`, `Destination`,
  `Transformation`, `TransformWork`, `TransformResult`, `CheckoutPath`,
  `CheckoutFileSystem`, `Metadata`, `Info`, `MigrationInfo`.

## Phase 4 — Transformations (`transform/`)

- ⬜ `Replace`, `Move`/`Copy`/`Remove`, `Sequence`, `TransformationRegistry`,
  `SkylarkTransformation`, `ExplicitReversal`, `TodoReplace`, `Scrubber`,
  `metadata/*` (message/label manipulation), `debug/*`, `patch/*`.

## Phase 5 — Git support (`git/`) — uses LibGit2Sharp

Largest single module (175 files). Port in slices:

- ⬜ Core plumbing: `GitRepository`, `GitRevision`, `GitReference`,
  `GitEnvironment`, `GitCredential`, `RefspecConverter`, `GitOptions`.
  Prefer LibGit2Sharp; keep a `git` CLI runner for gaps.
- ⬜ Origin/Destination: `GitOrigin`, `GitDestination`, `GitModule`,
  `GitMirror`, `ChangeReader`, writer hooks.
- ⬜ GitHub: `github/api` client (System.Text.Json + HttpClient),
  `GitHubOrigin`, `GitHubPrDestination`, `GitHubPrOrigin`, `github/util`.
- ⬜ Gerrit: `gerritapi` client, `GerritOrigin`, `GerritDestination`.
- ⬜ GitLab: `gitlab/api`, origin/destination.
- ⬜ `version/` resolvers.

## Phase 6 — Other origins/destinations & modules

- ⬜ `folder/` (FolderOrigin/FolderDestination) — good early integration target.
- ⬜ `remotefile/`, `archive/`, `hashing/`, `http/`, `format/` (buildifier),
  `hg/` (Mercurial), `go/`, `rust/`, `python/`, `tsjs/`, `toml/`, `json/`,
  `xml/`, `html/`, `re2/`, `buildozer/`, `checks/`, `approval/`, `feedback/`,
  `action/`, `credentials/`, `treestate/`, `monitor/`, `regenerate/`,
  `onboard/`, `configgen/`, `doc/` (reference doc generator).

## Phase 7 — CLI (`src/Copybara.Cli`)

- ⬜ Arg parsing (replace JCommander). Options contributed per-module, à la
  `Options.getAll()`. Lightweight custom parser matching upstream flag names.
- ⬜ `Main` orchestration (mirror `Main.java`): console setup, logging config,
  module set creation, command dispatch, exit codes, error handling.
- ⬜ Commands: `MigrateCmd`, `InfoCmd`, `ValidateCmd`, `HelpCmd`, `VersionCmd`,
  `RegenerateCmd`, `OnboardCmd`/`GeneratorCmd`.
- ⬜ `PackAsTool` metadata, `build-data` version embedding.

## Phase 8 — Tests, docs, polish

- ⬜ Port high-value tests from `javatests/` (glob, author, replace, workflow,
  git origin/destination round-trips).
- ⬜ End-to-end smoke test: folder→folder migrate with a `core.replace`.
- ⬜ Reference doc generation parity (optional).
- ⬜ Performance pass; RE2 semantics decision.

---

## Cross-cutting decisions / open questions

- 🔬 **RE2 vs .NET Regex** for `core.replace` — start with .NET Regex, flag
  divergences.
- 🔬 **Async vs sync** — repo I/O is naturally async in .NET; upstream is
  blocking. Decide per-boundary; keep the engine mostly synchronous initially to
  stay close to the source, use async only at process/network edges.
- 🔬 **Path handling** — Java uses `java.nio.file.Path` + Jimfs in tests. Use
  `string` + `System.IO`, and an in-memory filesystem abstraction for tests if
  needed.
- 🔬 **git CLI dependency** — LibGit2Sharp covers most, but shallow fetch,
  some refspec and credential-helper behaviors may still require the `git`
  binary. Keep `GitEnv`/`CommandRunner` available as a fallback.
</content>
