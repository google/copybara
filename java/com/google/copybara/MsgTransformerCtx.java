package com.google.copybara;

import com.google.copybara.Origin.Reference;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.syntax.SkylarkList;
import com.google.devtools.build.lib.syntax.SkylarkList.MutableList;

/**
 * The context of the set of changes being processed by the workflow. This object is passed to the
 * user defined functions in Skylark so that they can personalize the commit message.
 */
@SkylarkModule(name = "change_ctx",
    category = SkylarkModuleCategory.BUILTIN,
    doc = "The context of the set of changes being processed by the workflow. "
        + "It includes information about changes like: author, change message, "
        + "labels, etc. You receive a change_ctx object as an argument to the <code>"
        + Core.MESSAGE_TRANSFORMERS_FIELD + "</code> function used in <code>core.workflow</code>")
public class MsgTransformerCtx<R extends Reference> {

  private String message;
  private Author author;
  private final MutableList<Change<R>> currentChanges;
  private final MutableList<Change<R>> alreadyMigrated;

  public MsgTransformerCtx(String message, Author author, Iterable<Change<R>> currentChanges,
      Iterable<Change<R>> alreadyMigrated) {
    this.message = message;
    this.author = author;
    this.currentChanges = new MutableList<>(currentChanges);
    this.alreadyMigrated = new MutableList<>(alreadyMigrated);
  }

  @SkylarkCallable(name = "message", doc = "Message to be used in the change")
  public String getMessage() {
    return message;
  }

  @SkylarkCallable(name = "author", doc = "Author to be used in the change")
  public Author getAuthor() {
    return author;
  }

  @SkylarkCallable(name = "set_message", doc = "Update the message to be used in the change")
  public void setMessage(String message) {
    this.message = message;
  }

  @SkylarkCallable(name = "set_author", doc = "Update the author to be used in the change")
  public void setAuthor(Author author) {
    this.author = author;
  }

  @SkylarkCallable(name = "current_changes", doc = "List of changes that will be migrated")
  public SkylarkList<Change<R>> getChanges() {
    return currentChanges;
  }

  @SkylarkCallable(name = "migrated_changes", doc =
      "List of changes that where migrated in previous Copybara executions or if using ITERATIVE"
          + " mode in previous iterations of this workflow.")
  public SkylarkList<Change<R>> getMigratedChanges() {
    return alreadyMigrated;
  }
}
