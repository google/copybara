# Work-in-progress ports (interrupted)

These files are **partial ports** whose porting agents were interrupted by an
account session/usage limit before completion. They are kept **outside** the
`src/` tree so they are not part of the build (the solution stays green), and so
the substantial work already done is not lost.

To finish the port, move each file back to its intended location under
`src/Copybara.Core/` and complete/reconcile it against the Java source in `java/`:

| WIP file | Target location | Notes |
|----------|-----------------|-------|
| `Git/GitModule.cs` | `src/Copybara.Core/Git/GitModule.cs` | ~2188/3677 lines ported; references the git provider classes below. Finish the remaining `git.*` factory methods and reconcile provider ctors. |
| `Git/GitHubPrOriginOptions.cs` | `src/Copybara.Core/Git/` | Partial; finish + reconcile with `GitHubPrOrigin`. |
| `Git/GerritOptions.cs` | `src/Copybara.Core/Git/` | Partial. |
| `Onboard/` | `src/Copybara.Core/Onboard/` | ~8 files ported (of ~31); the input/generator framework needs completing. |

Still entirely un-ported (see `TODO.md`): most GitHub/Gerrit/GitLab **provider**
classes (`GitHubPrOrigin`, `GitHubPrDestination`, `GerritOrigin`,
`GerritDestination`, `GitLabMrOrigin`, `GitLabMrDestination`, endpoints, write
hooks, approvals validators), and `feedback/`, `regenerate/`, `configgen/`, and
the rest of `doc/`.
</content>
