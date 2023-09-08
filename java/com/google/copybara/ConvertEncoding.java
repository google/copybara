/*
 * Copyright (C) 2023 Google LLC
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

import com.google.copybara.exception.NonReversibleValidationException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.treestate.TreeState.FileState;
import com.google.copybara.util.Glob;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Convert encoding for a set of files
 */
public class ConvertEncoding implements Transformation {

  private final Charset before;
  private final Charset after;
  private final Glob paths;

  public ConvertEncoding(Charset before, Charset after, Glob paths) {
    this.before = before;
    this.after = after;
    this.paths = paths;
  }

  @Override
  public TransformationStatus transform(TransformWork work)
      throws IOException, ValidationException, RepoException {
    Path checkoutDir = work.getCheckoutDir();
    Set<FileState> files = new HashSet<>();
    for (FileState f : work.getTreeState().find(paths.relativeTo(checkoutDir))) {
      Files.writeString(f.getPath(), Files.readString(f.getPath(), before), after);
      files.add(f);
    }
    work.getTreeState().notifyModify(files);
    return files.isEmpty()
        ? TransformationStatus.noop("Glob didn't match any file")
        : TransformationStatus.success();
  }

  @Override
  public Transformation reverse() throws NonReversibleValidationException {
    return new ConvertEncoding(after, before, paths);
  }

  @Override
  public String describe() {
    return "convert_encoding";
  }
}
