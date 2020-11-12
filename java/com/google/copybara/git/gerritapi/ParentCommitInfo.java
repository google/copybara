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

package com.google.copybara.git.gerritapi;

import com.google.api.client.util.Key;
import com.google.common.base.MoreObjects;
import javax.annotation.Nullable;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Printer;
import net.starlark.java.eval.StarlarkValue;

/** Restricted version of {@link CommitInfo} for describing parents */
@SuppressWarnings("unused")
@StarlarkBuiltin(
    name = "gerritapi.ParentCommitInfo",
    doc = "Gerrit parent commit information.")
public class ParentCommitInfo implements StarlarkValue {
  @Key private String commit;
  @Key private String subject;

  @StarlarkMethod(
      name = "commit",
      doc =
          "The commit ID. Not set if included in a RevisionInfo entity that is contained "
              + "in a map which has the commit ID as key.",
      structField = true,
      allowReturnNones = true)
  @Nullable
  public String getCommit() {
    return commit;
  }

  @StarlarkMethod(
      name = "subject",
      doc = "The subject of the commit (header line of the commit message).",
      structField = true,
      allowReturnNones = true)
  @Nullable
  public String getSubject() {
    return subject;
  }

  @Override
  public void repr(Printer printer) {
    printer.append(toString());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("commit", commit)
        .add("subject", subject)
        .toString();
  }
}
