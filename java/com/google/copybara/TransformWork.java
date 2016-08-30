/*
 * Copyright (C) 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.copybara;

import com.google.common.base.Preconditions;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import java.nio.file.Path;

/**
 * Contains information related to an on-going process of repository transformation.
 *
 * This object is passed to the user defined functions in Skylark so that they can personalize the
 * commit message, change the author or in the future run custom transformations.
 */
@SkylarkModule(name = "TransformWork",
    category = SkylarkModuleCategory.BUILTIN,
    doc = "Data about the set of changes that are being migrated. "
        + "It includes information about changes like: the author to be used for commit, "
        + "change message, etc. You receive a TransformWork object as an argument to the <code>"
        + "transformations</code> functions used in <code>core.workflow</code>")
public final class TransformWork {

  private final Path checkoutDir;
  private Metadata metadata;
  private final Changes changes;

  public TransformWork(Path checkoutDir, Metadata metadata, Changes changes) {
    this.checkoutDir = Preconditions.checkNotNull(checkoutDir);
    this.metadata = Preconditions.checkNotNull(metadata);
    this.changes = changes;
  }

  /**
   * The path containing the repository state to transform. Transformation should be done in-place.
   */
  public Path getCheckoutDir() {
    return checkoutDir;
  }

  /**
   * A description of the migrated changes to include in the destination's change description. The
   * destination may add more boilerplate text or metadata.
   */
  @SkylarkCallable(name = "message", doc = "Message to be used in the change", structField = true)
  public String getMessage() {
    return metadata.getMessage();
  }

  @SkylarkCallable(name = "author", doc = "Author to be used in the change", structField = true)
  public Author getAuthor() {
    return metadata.getAuthor();
  }

  @SkylarkCallable(name = "set_message", doc = "Update the message to be used in the change")
  public void setMessage(String message) {
    this.metadata = new Metadata(Preconditions.checkNotNull(message, "Message cannot be null"),
        metadata.getAuthor());
  }

  @SkylarkCallable(name = "set_author", doc = "Update the author to be used in the change")
  public void setAuthor(Author author) {
    this.metadata = new Metadata(metadata.getMessage(),
        Preconditions.checkNotNull(author, "Author cannot be null"));
  }

  @SkylarkCallable(name = "changes", doc = "List of changes that will be migrated",
      structField = true)
  public Changes getChanges() {
    return changes;
  }
}
