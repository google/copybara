# How 3-Way Diff Works in Copybara CHANGE_REQUEST Mode

## 🎯 **Overview**

The 3-way diff in Copybara's CHANGE_REQUEST mode is designed to merge changes from a source repository (like a GitHub PR) into a destination repository while preserving destination-specific changes that don't exist in the source.

## 🔄 **The Three Commits Explained**

### **1. Origin Commit (LHS - Left Hand Side)**
- **What it is**: The current state of your change request (e.g., PR head commit)
- **Repository**: Source repository (where the PR/change request exists)
- **Purpose**: Contains the new changes you want to migrate
- **Example**: `feature-branch-head-commit-abc123`

### **2. Baseline Commit (Middle)**
- **What it is**: The common ancestor between origin and destination
- **Repository**: Usually from the source repository (the base branch when the PR was created)
- **Purpose**: The "last known good state" that both sides diverged from
- **Example**: `main-branch-when-pr-was-created-def456`

### **3. Destination Commit (RHS - Right Hand Side)**
- **What it is**: The current state in the destination repository
- **Repository**: Destination repository (where changes will be migrated to)
- **Purpose**: Contains destination-specific changes that should be preserved
- **Example**: `destination-main-branch-current-ghi789`

## 🔧 **How the 3-Way Merge Works**

### **Step 1: File Discovery**
```java
// For each file in the origin (your PR)
Path originFile = originWorkdir.resolve(relativeFile);      // Your PR changes
Path baselineFile = baselineWorkdir.resolve(relativeFile);  // Common ancestor
Path destinationFile = destinationWorkdir.resolve(relativeFile); // Destination state
```

### **Step 2: Merge Decision Logic**
```java
// All 3 files must exist to perform a merge
if (!Files.exists(destinationFile) || !Files.exists(baselineFile) || !match) {
  return FileVisitResult.CONTINUE; // Skip this file
}

// If destination hasn't changed from baseline, no merge needed
if (compareFileContents(destinationFile, baselineFile)) {
  return FileVisitResult.CONTINUE; // Use origin file as-is
}

// If destination and origin are the same, no merge needed
if (compareFileContents(destinationFile, originFile)) {
  return FileVisitResult.CONTINUE; // No conflicts
}

// Need to perform 3-way merge
performThreeWayMerge(originFile, baselineFile, destinationFile);
```

### **Step 3: diff3 Command Execution**
```bash
diff3 -m --label "origin_file" --label "baseline_file" --label "dest_file" \
  origin_file baseline_file dest_file
```

**Parameters:**
- `origin_file`: Your PR changes (what you want to apply)
- `baseline_file`: Common ancestor (what both sides started from)
- `dest_file`: Current destination state (what exists now)
- `-m`: Merge mode (output merged result)

## 📋 **Real-World Example**

Let's say you have a GitHub PR that modifies a file:

### **Scenario Setup**
```
Timeline:
1. main branch: commit A (baseline)
2. You create PR branch from A, make changes → commit B (origin)
3. Meanwhile, destination repo evolved from A → commit C (destination)
```

### **File States**
```java
// Original file at baseline (commit A)
public class Example {
  public void method1() { /* original */ }
  public void method2() { /* original */ }
}

// Your PR changes (commit B - origin)
public class Example {
  public void method1() { /* your changes */ }
  public void method2() { /* original */ }
  public void method3() { /* your addition */ }
}

// Destination changes (commit C - destination)  
public class Example {
  public void method1() { /* original */ }
  public void method2() { /* destination changes */ }
  public void method4() { /* destination addition */ }
}
```

### **3-Way Merge Result**
```java
// Merged result
public class Example {
  public void method1() { /* your changes */ }        // From origin
  public void method2() { /* destination changes */ } // From destination
  public void method3() { /* your addition */ }       // From origin
  public void method4() { /* destination addition */ } // From destination
}
```

## 🎯 **Key Concepts**

### **1. Origin as Source of Truth**
```java
// The origin is treated as the source of truth. Files that exist at baseline and destination
// but not in the origin will be deleted. Files that exist in the destination but not in the
// origin or in the baseline will be considered "destination only" and propagated.
```

### **2. Destination-Only Files**
- Files that exist **only** in destination (not in origin or baseline) are **preserved**
- These represent destination-specific customizations

### **3. Deleted Files**
- If a file exists in baseline and destination but **not** in origin → **deleted in destination**
- If a file exists in baseline and origin but **not** in destination → **added to destination**

### **4. Conflict Resolution**
```bash
# When diff3 detects conflicts, it outputs conflict markers:
<<<<<<< origin_file
Your changes
=======
Destination changes  
>>>>>>> dest_file
```

## 🔍 **Debugging the 3-Way Merge**

With the logging I added, you'll see:

```
COPYBARA_MERGE_IMPORT: Starting 3-way merge process
COPYBARA_MERGE_IMPORT: The 3 commits being merged are:
COPYBARA_MERGE_IMPORT: 1. Origin commit (current): abc123def456
COPYBARA_MERGE_IMPORT: 2. Destination commit: ghi789jkl012
COPYBARA_MERGE_IMPORT: 3. Baseline commit (common ancestor): def456ghi789

COPYBARA_DIFF3: Executing 3-way merge command: diff3 -m --label origin_file --label baseline_file --label dest_file /tmp/origin/file.java /tmp/baseline/file.java /tmp/dest/file.java
COPYBARA_DIFF3: File 1 (origin): /tmp/origin/file.java
COPYBARA_DIFF3: File 2 (baseline): /tmp/baseline/file.java  
COPYBARA_DIFF3: File 3 (destination): /tmp/dest/file.java
COPYBARA_DIFF3: 3-way merge completed successfully
```

## 📊 **Summary**

The 3-way diff ensures that:
1. **Your changes** (from the PR/change request) are applied
2. **Destination-specific changes** are preserved
3. **Conflicts** are detected and can be resolved
4. **File additions/deletions** are handled correctly

This allows you to migrate changes from a source repository while maintaining destination-specific customizations that shouldn't be lost.

