# How Copybara Finds the Baseline

## Overview
The line `runHelper.getOriginReader().findBaseline(runHelper.getResolvedRef(), originLabelName)` in `WorkflowMode.java` is responsible for finding the baseline commit in the origin repository. This is a crucial step in determining what changes need to be migrated.

## Step-by-Step Process

### 1. **Entry Point** (`WorkflowMode.java:260`)
```java
baseline = runHelper.getOriginReader().findBaseline(runHelper.getResolvedRef(), originLabelName)
```

- `runHelper.getResolvedRef()`: Gets the current revision (e.g., HEAD of a branch)
- `originLabelName`: The label name to search for (typically "GitOrigin-RevId")
- Returns an `Optional<Baseline<O>>` containing the baseline information

### 2. **Default Implementation** (`Origin.java:326-331`)
```java
default Optional<Baseline<R>> findBaseline(R startRevision, String label)
    throws RepoException, ValidationException {
  FindLatestWithLabel<R> visitor = new FindLatestWithLabel<>(startRevision, label);
  visitChanges(startRevision, visitor);
  return visitor.getBaseline();
}
```

This creates a visitor that will search through the commit history to find the latest commit containing the specified label.

### 3. **FindLatestWithLabel Visitor** (`Origin.java:346-376`)
```java
class FindLatestWithLabel<R extends Revision> implements ChangesVisitor {
  private final R startRevision;
  private final String label;
  @Nullable
  private Baseline<R> baseline;

  @Override
  public VisitResult visit(Change<? extends Revision> input) {
    // Skip the start revision itself
    if (input.getRevision().asString().equals(startRevision.asString())) {
      return VisitResult.CONTINUE;
    }
    
    // Check if this commit has the label we're looking for
    ImmutableMap<String, Collection<String>> labels = input.getLabels().asMap();
    if (!labels.containsKey(label)) {
      return VisitResult.CONTINUE;
    }
    
    // Found the label! Create baseline and stop searching
    baseline = new Baseline<>(Iterables.getLast(labels.get(label)),
        (R) input.getRevision());
    return VisitResult.TERMINATE;
  }
}
```

**Key Logic**:
- **Skips the start revision**: Doesn't consider the current commit as a baseline
- **Searches backwards**: Goes through commit history chronologically
- **Looks for labels**: Checks if each commit contains the specified label (e.g., "GitOrigin-RevId")
- **Uses latest occurrence**: If a label appears multiple times, uses the last one
- **Stops on first match**: Returns `TERMINATE` when a baseline is found

### 4. **Commit History Traversal** (`GitOrigin.java:646-656`)
```java
@Override
public void visitChanges(GitRevision start, ChangesVisitor visitor)
    throws RepoException, ValidationException {
  ChangeReader.Builder queryChanges = changeReaderBuilder(repoUrl).setFirstParent(firstParent);
  ImmutableSet<String> roots = originFiles.roots();

  GitVisitorUtil.visitChanges(
      start, input -> affectsRoots(roots, input.getChangeFiles())
          ? visitor.visit(input)
          : VisitResult.CONTINUE,
      queryChanges, generalOptions, "origin", gitOptions.visitChangePageSize);
}
```

This method:
- Creates a `ChangeReader` to query git history
- Uses `GitVisitorUtil.visitChanges()` to traverse commits
- Filters commits based on `originFiles` (only considers commits affecting relevant files)
- Calls the visitor for each qualifying commit

### 5. **Git History Query** (`GitVisitorUtil.java:36-70`)
```java
static void visitChanges(
    GitRevision start,
    ChangesVisitor visitor,
    ChangeReader.Builder queryChanges,
    GeneralOptions generalOptions,
    String type,
    int visitChangePageSize)
    throws RepoException, ValidationException {
  // ... pagination logic ...
  while (!finished) {
    ImmutableList<Change<GitRevision>> result;
    result = queryChanges.setSkip(skip).setLimit(visitChangePageSize).build().run(start).reverse();
    
    for (Change<GitRevision> current : result) {
      if (visitor.visit(current) == VisitResult.TERMINATE) {
        finished = true;
        break;
      }
    }
  }
}
```

**Process**:
- **Pagination**: Processes commits in batches to handle large repositories
- **Reverse order**: Gets commits in chronological order (oldest first)
- **Visitor pattern**: Calls the visitor for each commit
- **Early termination**: Stops when visitor returns `TERMINATE`

### 6. **Git Log Execution** (`ChangeReader.java:100-131`)
```java
ImmutableList<Change<GitRevision>> run(
    @Nullable GitRevision fromRev,
    GitRevision toRev,
    boolean historyIsNonLinear,
    ImmutableMap<String, ImmutableListMultimap<String, String>> labels)
    throws RepoException, ValidationException {
  String refExpression = fromRev == null || historyIsNonLinear
      ? toRev.getSha1()
      : fromRev.getSha1() + ".." + toRev.getSha1();
  LogCmd logCmd = repository.log(refExpression).firstParent(firstParent).topoOrder(topoOrder);
  // ... configure log command ...
  return parseChanges(logCmd.includeFiles(true).includeMergeDiff(true).run(), labels, toRev);
}
```

This executes the actual `git log` command to retrieve commit history.

## Example Flow

Let's say we have a git repository with this history:
```
A (GitOrigin-RevId: origin_commit_1)
B (GitOrigin-RevId: origin_commit_2)  
C (no label)
D (GitOrigin-RevId: origin_commit_3)
E (current HEAD)
```

**Search Process**:
1. **Start at E**: Begin search from current HEAD
2. **Skip E**: Don't consider current commit as baseline
3. **Check D**: Found "GitOrigin-RevId: origin_commit_3" → **FOUND BASELINE!**
4. **Return**: `Baseline("origin_commit_3", D)`

## Key Points

### **What is a Baseline?**
- A baseline is a commit in the origin repository that was previously migrated to the destination
- It's identified by the presence of a label (like "GitOrigin-RevId") in the commit message
- The baseline represents the "last known good state" that was successfully migrated

### **Why Find Baseline?**
- **Incremental Migration**: Only migrate changes since the last successful migration
- **Avoid Duplicates**: Don't re-migrate commits that were already processed
- **Resume Point**: Know where to continue from if a migration was interrupted

### **Search Strategy**
- **Backwards Search**: Goes from current commit backwards through history
- **First Match Wins**: Stops at the first commit containing the label
- **Label Priority**: Uses the last occurrence if a label appears multiple times
- **File Filtering**: Only considers commits that affect files in `originFiles`

### **Special Cases**
- **No Baseline Found**: Returns `Optional.empty()` if no label is found
- **Multiple Labels**: Uses the last occurrence of the label in the commit
- **File Filtering**: Skips commits that don't affect the specified origin files
- **Pagination**: Handles large repositories by processing commits in batches

## Logging Integration

With the logging we added earlier, you can now see:
```
INFO: Starting GitOrigin-RevID search from commit abc123def456 with grep pattern: ^GitOrigin-RevId: 
INFO: Searched commit: SHA=abc123def456, Author=John Doe, Message=Current commit
INFO: Searched commit: SHA=def456ghi789, Author=Jane Smith, Message=Previous commit
INFO: Found GitOrigin-RevId label in commit def456ghi789: [origin_commit_123]
INFO: GitOrigin-RevID found: origin_commit_123 in commit def456ghi789 for file src/main.java
```

This shows exactly which commits are being searched and when the baseline is found.


