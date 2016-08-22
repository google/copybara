package com.google.copybara;

import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import java.util.Map;

/**
 * The context of the set of changes being processed by the workflow. This object is passed to the
 * user defined functions in Skylark so that they can personalize the commit message.
 * TODO(malcon): Change Change.originalAuthor with author and expose Change instead.
 */
@SkylarkModule(name = "change",
    category = SkylarkModuleCategory.BUILTIN,
    doc = "A change metadata. Contains information like author, change message or detected labels")
public class SkylarkChange {

  private final Author author;
  private final String changeRef;
  private final String message;
  private final Map<String, String> labels;

  public SkylarkChange(Author author, String changeRef, String message,
      Map<String, String> labels) {
    this.author = author;
    this.changeRef = changeRef;
    this.message = message;
    this.labels = labels;
  }

  @SkylarkCallable(name = "message", doc = "The message of the change")
  public String getMessage() {
    return message;
  }

  @SkylarkCallable(name = "first_line_message", doc = "The message of the change")
  public String getFirstLineMessage() {
    int idx = message.indexOf("\n");
    return idx == -1 ? message : message.substring(0, idx);
  }

  @SkylarkCallable(name = "author", doc = "The author of the change")
  public Author getAuthor() {
    return author;
  }

  @SkylarkCallable(name = "ref", doc = "A string identifier of the change.")
  public String ref() {
    return changeRef;
  }

  public SkylarkChange asMigrated() {
    return new SkylarkChange(author, changeRef, /*current=*/ message, labels);
  }

}
