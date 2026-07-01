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
- ✅ Create solution + project scaffold (`Copybara.slnx`):
  - `src/Copybara.Common`, `src/Starlark`, `src/Copybara.Core`,
    `src/Copybara.Cli` (`PackAsTool=true`, verified `dotnet pack` produces a tool
    package), `tests/Copybara.Tests`.
- ✅ Wire NuGet deps: `LibGit2Sharp`, `Microsoft.Extensions.Logging`, xUnit,
  FluentAssertions (`System.Collections.Immutable`/`System.Text.Json` are in-box).
- ✅ `.gitignore` for .NET (`bin/`, `obj/`, `*.user`, `nupkg/`).
- ✅ Whole solution builds clean (0 warnings/0 errors); 8 smoke tests pass.
- ⬜ CI: `dotnet build` + `dotnet test` GitHub Action.

## Phase 1 — Foundation (`Copybara.Common` + `Copybara.Core` primitives)

No Starlark dependency. Port these first; they unblock everything else.

- ✅ `Copybara.Common`: `Preconditions`, `ImmutableListMultimap<K,V>` + builder.
  (String helpers added ad hoc where needed.)
- ✅ `exception/` → `Copybara.Exceptions` — all 13 classes ported with correct
  hierarchy; `ValidationException.CheckCondition` handles printf-style `%s`
  format strings via an internal `%`→`{}` translator.
- ✅ `util/` core (subset) → `Copybara.Util`: `ExitCode`, full `Glob` subsystem
  (`Glob`/`GlobAtom`/`SequenceGlob`/`ReadablePathMatcher`/`GlobPathMatcher`/
  `IPathMatcher`), `FileUtil`, `DirFactory`, `Identity`, `TablePrinter`,
  `CommandOutput(WithStatus)`, `CommandRunner` + shell stack.
  - ⬜ Still TODO in util: `DiffUtil`, `CommandLineDiffUtil`, `MergeImportTool`,
    `AutoPatchUtil`, `ConsistencyFile`, `ApplyDestinationPatch`, `RenameDetector`,
    `ScpUtil`, `OriginUtil`, `RepositoryUtil`, `EnumMapConverter`.
- ✅ `util/console/` → `Copybara.Util.Console` — all 17 files (`Console`,
  `AnsiConsole`, `LogConsole`, `FileConsole`, `NoPromptConsole`,
  `CapturingConsole`, `MultiplexingConsole`, `PrefixConsole`, `Consoles`, …).
- ✅ `revision/` → `Copybara.Revision`: `IRevision`, `Change<R>`, `Changes`, `OriginRef`.
- ✅ `authoring/` → `Copybara.Authoring`: `Author`, `AuthorParser`, `Authoring`,
  `InvalidAuthorException` (as Starlark values).
- ✅ `templatetoken/` → `Copybara.TemplateToken`.
- ⬜ `profiler/` → `Copybara.Profiler`.

## Phase 2 — Starlark interpreter (`src/Starlark`)

Port of `java/third_party/bazel/main/java/net/starlark/java`. This is a
self-contained interpreter and the critical dependency for config loading.
Sub-packages: `annot`, `syntax` (lexer/parser/AST), `eval` (values, evaluator,
builtins), `lib` (json, proto, etc.), `spelling`.

- ✅ `annot/` — `[StarlarkBuiltin]`, `[StarlarkMethod]`, `[Param]`, `[ParamType]`.
- ✅ `syntax/` — `Lexer`, `Parser`, all AST node types, `Location`, `TokenKind`,
  `FileOptions`, `SyntaxError`, `NodeVisitor`, `StarlarkFile`/`Program`, plus the
  resolver/type-system subset (`Resolver`, `Types`, `StarlarkType`, `TypeChecker`,
  `TypeTagger`, `TypeConstructor`, `TypeContext`).
- 🚧 `eval/` — DONE: value types (`StarlarkInt/Float/List/Tuple/Dict/RangeList`,
  `Sequence`), `Mutability`, `StarlarkSemantics`, `Printer`, `Module`,
  `StarlarkThread`, `EvalUtils`, `StarlarkValue`/`NoneType`/`EvalException`/
  `Starlark` helpers, callable interfaces.
  - ✅ Evaluator + dispatch: `Eval` tree-walker, reflective dispatch
    (`CallUtils`/`MethodDescriptor`/`ParamDescriptor`/`BuiltinFunction`/
    `StarlarkFunction`) mapping `[StarlarkMethod]`→calls, `MethodLibrary` +
    `StringModule` builtins, and `Starlark.ExecFile`/`Eval`/`Call` drivers.
    Parse→resolve→execute of real Starlark works (functions, comprehensions,
    closures, builtins, string methods) — verified by xUnit `StarlarkEvalTests`.
  - ⬜ Still deferred: `StarlarkSet` + its EvalUtils operator branches, `float`
    builtin, flag-guarded params, dynamic arg/return type-checking.
- ✅ `spelling/` — `SpellChecker`.
- ⬜ `lib/json` — `Json` module (interop with `System.Text.Json`).
- ⬜ Reflection strategy: reflect over `[StarlarkMethod]`/`[Param]` at startup,
  cached per type (source generators a later optimization).
- 🔬 Validate with upstream Starlark eval tests once the evaluator lands.

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

- ✅ **RE2 vs .NET Regex** — DECIDED: use the native .NET regex engine
  (`System.Text.RegularExpressions`). Accepted deviation from upstream re2j.
  Flag any divergences observed during porting/testing.
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
