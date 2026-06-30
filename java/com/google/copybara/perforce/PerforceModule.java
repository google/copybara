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

import static com.google.copybara.config.SkylarkUtil.checkNotEmpty;

import com.google.common.base.Preconditions;
import com.google.copybara.Options;
import com.google.copybara.config.LabelsAwareModule;
import com.google.copybara.doc.annotations.UsesFlags;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.StarlarkValue;

/** Main module for Perforce (Helix Core) origins and destinations. */
@StarlarkBuiltin(
    name = "perforce",
    doc = "Set of functions to define Perforce (Helix Core) origins and destinations.")
@UsesFlags(PerforceOptions.class)
public class PerforceModule implements LabelsAwareModule, StarlarkValue {

  protected final Options options;

  public PerforceModule(Options options) {
    this.options = Preconditions.checkNotNull(options);
  }

  @StarlarkMethod(
      name = "origin",
      doc =
          "<b>EXPERIMENTAL:</b> Defines a Perforce origin that reads a stream at a submitted"
              + " changelist.",
      parameters = {
        @Param(
            name = "stream",
            named = true,
            doc =
                "The Perforce stream to read from, e.g. <code>//stream/main</code>. The connection"
                    + " details (server, user, ticket) come from the <code>--perforce-*</code>"
                    + " flags or the standard P4PORT/P4USER/P4PASSWD environment variables."),
        @Param(
            name = "ref",
            named = true,
            defaultValue = "\"head\"",
            doc =
                "The default reference used to read a revision. Either a submitted changelist"
                    + " number (e.g. <code>\"12345\"</code>) or the literal <code>\"head\"</code>"
                    + " for the most recent submitted changelist on the stream."),
      })
  public PerforceOrigin origin(String stream, String ref) throws EvalException {
    return PerforceOrigin.newPerforceOrigin(options, checkNotEmpty(stream, "stream"), ref);
  }

  @StarlarkMethod(
      name = "destination",
      doc =
          "<b>EXPERIMENTAL:</b> Defines a Perforce destination that submits each migrated change as"
              + " a changelist on a stream.",
      parameters = {
        @Param(
            name = "stream",
            named = true,
            doc =
                "The Perforce stream to submit to, e.g. <code>//stream/main</code>. Connection"
                    + " details come from the <code>--perforce-*</code> flags or the standard"
                    + " P4PORT/P4USER/P4PASSWD environment variables."),
        @Param(
            name = "submit_as_author",
            named = true,
            defaultValue = "False",
            doc =
                "If true, each changelist is attributed to the original change author (a Perforce"
                    + " user is created on demand). Requires the connecting user to have permission"
                    + " to create users and submit on their behalf. If false, changelists are"
                    + " submitted as the connecting user and the author is kept in the description."),
      })
  public PerforceDestination destination(String stream, boolean submitAsAuthor)
      throws EvalException {
    return PerforceDestination.newPerforceDestination(
        options, checkNotEmpty(stream, "stream"), submitAsAuthor);
  }
}
