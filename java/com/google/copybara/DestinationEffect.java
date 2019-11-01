/*
 * Copyright (C) 2018 Google Inc.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.skylarkinterface.SkylarkPrinter;
import com.google.devtools.build.lib.skylarkinterface.SkylarkValue;
import com.google.devtools.build.lib.syntax.SkylarkList;
import java.util.Objects;
import javax.annotation.Nullable;

/** An effect happening in the destination as a consequence of the migration */
@SkylarkModule(
  name = "destination_effect",
  category = SkylarkModuleCategory.BUILTIN,
  doc = "Represents an effect that happened in the destination due to a single migration"
)
@SuppressWarnings("unused")
public class DestinationEffect implements SkylarkValue {
  private final Type type;
  private final String summary;
  private final ImmutableList<OriginRef> originRefs;
  @Nullable private final DestinationRef destinationRef;
  private final ImmutableList<String> errors;

  public DestinationEffect(
      Type type,
      String summary,
      Iterable<? extends OriginRef> originRefs,
      @Nullable DestinationRef destinationRef) {
    this(type, summary, originRefs, destinationRef, ImmutableList.of());
  }

  public DestinationEffect(
      Type type,
      String summary,
      Iterable<? extends OriginRef> originRefs,
      @Nullable DestinationRef destinationRef,
      Iterable<String> errors) {
    this.type = Preconditions.checkNotNull(type);
    this.summary = Preconditions.checkNotNull(summary);
    this.originRefs = ImmutableList.copyOf(Preconditions.checkNotNull(originRefs));
    this.destinationRef = destinationRef;
    this.errors = ImmutableList.copyOf(Preconditions.checkNotNull(errors));
  }

  /** Returns the origin references included in this effect. */
  public ImmutableList<OriginRef> getOriginRefs() {
    return originRefs;
  }

  @SkylarkCallable(
    name = "origin_refs",
    doc = "List of origin changes that were included in" + " this migration",
    structField = true
  )
  public final SkylarkList<? extends OriginRef> getOriginRefsSkylark() {
    return SkylarkList.createImmutable(originRefs);
  }

  /** Return the type of effect that happened: Create, updated, noop or error */
  public Type getType() {
    return type;
  }

  @SkylarkCallable(
    name = "type",
    doc =
        "Return the type of effect that happened: CREATED, UPDATED, NOOP, INSUFFICIENT_APPROVALS"
            + " or ERROR",
    structField = true
  )
  public String getTypeSkylark() {
    return type.toString();
  }

  /** Textual summary of what happened. Users of this class should not try to parse this field. */
  @SkylarkCallable(
    name = "summary",
    doc =
        "Textual summary of what happened. Users of this class should not try to parse this"
            + " field.",
    structField = true
  )
  public String getSummary() {
    return summary;
  }

  /**
   * Destination reference updated/created. Might be null if there was no effect. Might be set even
   * if the type is error (For example a synchronous presubmit test failed but a review was
   * created).
   */
  @SkylarkCallable(
    name = "destination_ref",
    doc =
        "Destination reference updated/created. Might be null if there was no effect. Might be"
            + " set even if the type is error (For example a synchronous presubmit test failed but"
            + " a review was created).",
    structField = true,
    allowReturnNones = true
  )
  @Nullable
  public DestinationRef getDestinationRef() {
    return destinationRef;
  }

  /**
   * List of errors that happened during the write to the destination. This can be used for example
   * for synchronous presubmit failures.
   */
  public ImmutableList<String> getErrors() {
    return errors;
  }

  @SkylarkCallable(
    name = "errors",
    doc = "List of errors that happened during the migration",
    structField = true
  )
  public final SkylarkList<String> getErrorsSkylark() {
    return SkylarkList.createImmutable(errors);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DestinationEffect that = (DestinationEffect) o;
    return type == that.type
        && Objects.equals(summary, that.summary)
        && Objects.equals(originRefs, that.originRefs)
        && Objects.equals(destinationRef, that.destinationRef)
        && Objects.equals(errors, that.errors);
  }

  @Override
  public void repr(SkylarkPrinter printer) {
    printer.append(toString());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("type", type)
        .add("summary", summary)
        .add("originRefs", originRefs)
        .add("destinationRef", destinationRef)
        .add("errors", errors)
        .toString();
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, summary, originRefs, destinationRef, errors);
  }

  /** Type of effect on the destination */
  public enum Type {
    /** A new review or change was created */
    CREATED,
    /** An existing review or change was updated */
    UPDATED,
    /**
     * The change was a noop. {@code destinationRef} might still be populated if the noop was
     * detected against an existing review or pending change.
     */
    NOOP,
    /** The effect couldn't happen because the change doesn't have enough approvals */
    INSUFFICIENT_APPROVALS,
    /**
     * A user attributable error happened that prevented the destination from creating/updating the
     * change.
     */
    ERROR,
    /**
     * An error not attributable to the user that could be retried (RepoException, IOException...)
     */
    TEMPORARY_ERROR,
    /**
     * A starting effect of a migration that is eventually expected to trigger another migration
     * asynchronously. This allows to have 'dependant' migrations defined by users.
     * An example of this: a workflow migrates code from a Gerrit review to a GitHub PR, and a
     * feedback migration migrates the test results from a CI in GitHub back to the Gerrit change.
     * This effect would be created on the former one.
     */
    STARTED,
  }

  /** Reference to the change/review read from the origin. */
  @SkylarkModule(
      name = "origin_ref",
      category = SkylarkModuleCategory.BUILTIN,
      doc = "Reference to the change/review in the origin."
  )
  public static class OriginRef implements SkylarkValue {
    private final String ref;

    @VisibleForTesting
    public OriginRef(String id) {
      this.ref = Preconditions.checkNotNull(id);
    }

    /** Origin reference*/
    @SkylarkCallable(name = "ref", doc = "Origin reference ref", structField = true)
    public String getRef() {
      return ref;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      OriginRef originRef = (OriginRef) o;
      return Objects.equals(ref, originRef.ref);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(ref);
    }

    @Override
    public void repr(SkylarkPrinter printer) {
      printer.append(toString());
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("ref", ref)
          .toString();
    }
  }


  /** Reference to the change/review created/updated on the destination. */
  @SkylarkModule(
    name = "destination_ref",
    category = SkylarkModuleCategory.BUILTIN,
    doc = "Reference to the change/review created/updated on the destination."
  )
  public static class DestinationRef implements SkylarkValue {
    @Nullable private final String url;
    private final String id;
    private final String type;

    public DestinationRef(String id, String type, @Nullable String url) {
      this.id = Preconditions.checkNotNull(id);
      this.type = Preconditions.checkNotNull(type);
      this.url = url;
    }

    /** Destination reference id */
    @SkylarkCallable(name = "id", doc = "Destination reference id", structField = true)
    public String getId() {
      return id;
    }

    /**
     * Type of reference created. Each destination defines its own and guarantees to be more stable
     * than urls/ids.
     */
    @SkylarkCallable(
      name = "type",
      doc =
          "Type of reference created. Each destination defines its own and guarantees to be more"
              + " stable than urls/ids",
      structField = true
    )
    public String getType() {
      return type;
    }

    /** Url, if any, of the destination change */
    @SkylarkCallable(
      name = "url",
      doc = "Url, if any, of the destination change",
      structField = true,
      allowReturnNones = true
    )
    @Nullable
    public String getUrl() {
      return url;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      DestinationRef that = (DestinationRef) o;
      return Objects.equals(url, that.url)
          && Objects.equals(id, that.id)
          && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
      return Objects.hash(url, id, type);
    }

    @Override
    public void repr(SkylarkPrinter printer) {
      printer.append(toString());
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("url", url)
          .add("id", id)
          .add("type", type)
          .toString();
    }
  }
}
