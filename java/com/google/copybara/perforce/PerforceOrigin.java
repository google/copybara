/*
 * Copyright (C) 2026 Google Inc.
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

package com.google.copybara.perforce;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.copybara.util.Glob.affectsRoots;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.Origin;
import com.google.copybara.Origin.Reader.ChangesResponse.EmptyReason;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.exception.CannotResolveRevisionException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.revision.Change;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.Glob;
import java.io.IOException;
import java.nio.file.Path;
import javax.annotation.Nullable;

/** A Perforce (Helix Core) origin that reads a stream at a submitted changelist. */
public class PerforceOrigin implements Origin<PerforceRevision> {

  /** Label used to persist the migrated changelist into destination commit messages. */
  public static final String PERFORCE_ORIGIN_REV_ID = "PerforceOrigin-RevId";

  private final GeneralOptions generalOptions;
  private final PerforceOptions perforceOptions;
  private final String stream;
  @Nullable private final String configRef;

  private PerforceOrigin(
      GeneralOptions generalOptions,
      PerforceOptions perforceOptions,
      String stream,
      @Nullable String configRef) {
    this.generalOptions = checkNotNull(generalOptions);
    this.perforceOptions = checkNotNull(perforceOptions);
    // Streams are referenced without a trailing slash, e.g. "//stream/main".
    this.stream = CharMatcher.is('/').trimTrailingFrom(checkNotNull(stream));
    this.configRef = configRef;
  }

  @VisibleForTesting
  public PerforceServer getServer() throws RepoException, ValidationException {
    return perforceOptions.server();
  }

  @Override
  public PerforceRevision resolve(@Nullable String reference)
      throws RepoException, ValidationException {
    String ref = Strings.isNullOrEmpty(reference) ? configRef : reference;
    PerforceServer server = getServer();

    // No explicit reference and no configured default: take the tip of the stream.
    if (Strings.isNullOrEmpty(ref) || ref.equals("head") || ref.equals("now")) {
      return new PerforceRevision(server.latestChange(stream), stream, /* timestamp= */ null);
    }

    int changelist = parseChangelist(ref);
    if (!server.changeExists(stream, changelist)) {
      throw new CannotResolveRevisionException(
          String.format("Changelist %d does not affect stream '%s'", changelist, stream));
    }
    return new PerforceRevision(changelist, stream, /* timestamp= */ null);
  }

  private int parseChangelist(String ref) throws ValidationException {
    // Accept both "12345" and the common "@12345" notation.
    String trimmed = ref.startsWith("@") ? ref.substring(1) : ref;
    try {
      return Integer.parseInt(trimmed);
    } catch (NumberFormatException e) {
      throw new ValidationException(
          String.format("Invalid Perforce reference '%s': expected a changelist number", ref));
    }
  }

  @Override
  public Reader<PerforceRevision> newReader(Glob originFiles, Authoring authoring) {
    return new ReaderImpl(originFiles);
  }

  class ReaderImpl implements Reader<PerforceRevision> {

    private final Glob originFiles;

    ReaderImpl(Glob originFiles) {
      this.originFiles = checkNotNull(originFiles);
    }

    @Override
    public void checkout(PerforceRevision revision, Path checkoutDir)
        throws RepoException, ValidationException {
      try {
        FileUtil.deleteRecursively(checkoutDir);
      } catch (IOException e) {
        throw new RepoException("Error preparing checkout directory " + checkoutDir, e);
      }
      getServer().syncStreamTo(stream, revision.getChangelist(), checkoutDir);
    }

    @Override
    public ChangesResponse<PerforceRevision> changes(
        @Nullable PerforceRevision fromRef, PerforceRevision toRef)
        throws RepoException, ValidationException {
      int from = fromRef == null ? 0 : fromRef.getChangelist();
      ImmutableList<Change<PerforceRevision>> changes =
          getServer().changes(stream, from, toRef.getChangelist());

      if (!changes.isEmpty()) {
        return ChangesResponse.forChanges(changes);
      }
      if (fromRef != null && toRef.getChangelist() <= fromRef.getChangelist()) {
        return ChangesResponse.noChanges(EmptyReason.TO_IS_ANCESTOR);
      }
      return ChangesResponse.noChanges(EmptyReason.NO_CHANGES);
    }

    @Override
    public Change<PerforceRevision> change(PerforceRevision ref)
        throws RepoException, ValidationException {
      return getServer().describe(stream, ref.getChangelist());
    }

    @Override
    public void visitChanges(PerforceRevision start, ChangesVisitor visitor)
        throws RepoException, ValidationException {
      ImmutableSet<String> roots = originFiles.roots();
      // Perforce stream history is linear, so walk every change up to 'start' newest first and
      // stop as soon as the visitor is satisfied.
      ImmutableList<Change<PerforceRevision>> changes =
          getServer().changes(stream, 0, start.getChangelist());
      for (Change<PerforceRevision> change : changes.reverse()) {
        if (!affectsRoots(roots, change.getChangeFiles())) {
          continue;
        }
        if (visitor.visit(change) == VisitResult.TERMINATE) {
          return;
        }
      }
    }
  }

  @Override
  public String getLabelName() {
    return PERFORCE_ORIGIN_REV_ID;
  }

  @Override
  public String getType() {
    return "perforce.origin";
  }

  @Override
  public ImmutableSetMultimap<String, String> describe(Glob originFiles) {
    ImmutableSetMultimap.Builder<String, String> builder =
        new ImmutableSetMultimap.Builder<String, String>()
            .put("type", getType())
            .put("stream", stream);
    if (configRef != null) {
      builder.put("ref", configRef);
    }
    return builder.build();
  }

  @Override
  public String toString() {
    return String.format("PerforceOrigin{stream = %s, ref = %s}", stream, configRef);
  }

  static PerforceOrigin newPerforceOrigin(Options options, String stream, @Nullable String ref) {
    return new PerforceOrigin(
        options.get(GeneralOptions.class), options.get(PerforceOptions.class), stream, ref);
  }
}
