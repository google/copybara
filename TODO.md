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

- ✅ `config/` → `Copybara.Config`: `Config`, `ConfigFile`, `PathBasedConfigFile`,
  `MapConfigFile`, `IMigration`, `ConfigValidator`, `SkylarkParser` (wired to the
  ported interpreter), `ConfigLoader`/`IConfigLoaderProvider`, `ILabelsAwareModule`,
  `ConfigWithDependencies`.
- ✅ Top-level engine types: `IOption`/`Options`, `GeneralOptions`, `ModuleSet`/
  `ModuleSupplier`, `CoreModule`/`CoreGlobal`, `Workflow<O,D>` (+ non-generic
  `Workflow.Create` factory), `WorkflowOptions`, `WorkflowMode`, `WorkflowRunHelper`,
  `IMigration`, `IOrigin`/`IDestination`, `ITransformation`, `TransformWork`,
  `TransformResult`, `CheckoutPath`, `CheckoutFileSystem`, `Metadata`, `Info`,
  `MigrationInfo`, plus `profiler/`, `effect/`, `monitor/`, `action/`, `approval/`,
  `treestate/`, `version/`.

## Phase 4 — Transformations (`transform/`)

- ✅ `Replace`, `CopyOrMove`/`Remove`, `Sequence`, `ExplicitReversal`,
  `IReversibleFunction`, `SkylarkConsole`, `FilterReplace`, `VerifyMatch`,
  `TodoReplace`, `SkylarkTransformation`, `transform/metadata/*`, `transform/debug/*`.
- ⬜ `transform/patch/*` (needs DiffUtil/patch tooling).

## Phase 5 — Git support (`git/`) — uses LibGit2Sharp / git CLI

Largest single module (~175 files). Port in slices:

- ✅ Core plumbing: `GitRepository` (git-CLI-backed via `CommandRunner`, faithful
  to upstream which shells out), `GitRevision`, `GitRepoType`, `GitEnvironment`,
  `GitCredential`, `Refspec`, `FetchResult`, `MergeResult`, `IntegrateLabel`,
  `SameGitTree`, exceptions.
- ✅ Origin/Destination base: `GitOrigin`, `GitDestination`, `GitDestinationReader`,
  `ChangeReader`, `GitVisitorUtil`, `Mirror`, `GitIntegrateChanges`, options, write hooks.
- ✅ `GitModule` (the `git` Starlark module — all 19 git.* factories wired).
- ✅ GitHub: `github/api` client (~50 files) + providers (`GitHubPrOrigin`,
  `GitHubPrDestination`, `GitHubEndPoint`, write hooks, approvals validators, `github/util`).
- ✅ Gerrit: `gerritapi` client (30) + providers (`GerritOrigin`/`GerritDestination`/`GerritEndpoint`).
- ✅ GitLab: `gitlab/api` client (18) + providers (`GitLabMrOrigin`/`GitLabMrDestination`).
- ✅ `git/version/` selectors. ✅ `hg/` (Mercurial).

## Phase 6 — Other origins/destinations & modules

- ✅ `folder/` (FolderOrigin/FolderDestination/FolderModule) — first ported origin/destination pair.
- ✅ `remotefile/`, `archive/` (zip/tar/gzip; xz/bz2 = TODO), `hashing/`,
  `http/` (HttpClient), `format/` (buildifier), `buildozer/`, `toml/`, `json/`,
  `xml/`, `html/`, `re2/`, `credentials/`, `approval/`, `action/`, `treestate/`,
  `monitor/`, `effect/`, `checks/` (minimal stub).
- ✅ `hg/` (Mercurial), `go/`, `rust/`, `python/`, `tsjs/` (npm).
- ✅ `feedback/`, `checks/`, `regenerate/`, `onboard/`, `configgen/`,
  `doc/` (reflection-based reference generator), `transform/patch/`.
- ✅ `starlark/StarlarkUtil`, `archive/util`.

## Source port: COMPLETE

Every source package under `java/com/google/copybara/**` and the vendored
`net.starlark.java` interpreter has a C# counterpart. ~666 C# files / ~98.5k LOC.
Whole solution builds 0 warnings / 0 errors; tests pass.

Intentionally NOT ported (superseded/obsolete): `jcommander/*` converters/validators
(replaced by the custom `Copybara.Cli.ArgParser`).

### Remaining integration / follow-up work (not source-porting)
- **Wire `ModuleSupplier.GetModules()`** to register the ported Starlark modules
  (`Core`, `git`, `folder`, `format`, `http`, `hashing`, `archive`, `remotefile`,
  `toml`/`json`/`xml`/`html`/`re2`, `go`/`rust`/`python`/`npm`, `credentials`, …)
  and `NewOptions()` to register every `IOption`. Currently stubbed empty — this is
  what makes `copybara migrate` load a real `copy.bara.sky` end-to-end.
- **Wire CLI commands**: `RegenerateCmd`/`OnboardCmd`/`GeneratorCmd` engines exist in
  Core with `Run(...)` entry points; add thin `ICopybaraCmd` adapters in `Copybara.Cli`.
- **Archive xz/bz2** (`TAR_XZ`/`TAR_BZ2`) need a codec (no in-box option) — currently
  throw with a `TODO(port)`.
- **Expand test coverage**: port high-value suites from `java/javatests/` and add an
  end-to-end folder→folder `migrate` smoke test.
- Verify a few `structField`/reflective-dispatch edge cases against real configs.

## Phase 7 — CLI (`src/Copybara.Cli`)

- ✅ Arg parsing (custom `ArgParser` replacing JCommander, reading `[Flag]`
  attributes off option objects), matching upstream flag names.
- ✅ `Main` orchestration (mirrors `Main.java`): console setup, logging config,
  module set creation, command dispatch, exit codes, error handling.
- ✅ Commands: `MigrateCmd`, `InfoCmd`, `ValidateCmd` (+ version/help).
- ⬜ Commands not yet ported: `RegenerateCmd`, `OnboardCmd`/`GeneratorCmd`.
- ✅ `PackAsTool` + package icon/readme. ⬜ `build-data` version embedding.

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
