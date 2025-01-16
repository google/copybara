/*
 * Copyright (C) 2023 Google Inc.
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

package com.google.copybara.compression;

import com.google.copybara.CheckoutPath;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.remotefile.extractutil.ExtractType;
import com.google.copybara.remotefile.extractutil.ExtractUtil;
import com.google.copybara.util.Glob;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.NoneType;
import net.starlark.java.eval.StarlarkList;
import net.starlark.java.eval.StarlarkValue;

/** Starlark utilities for working with compressed formats such as zip files. */
@StarlarkBuiltin(
    name = "compression",
    doc =
        "DEPRECATED. Use the `archive` module.\nModule for compression related starlark utilities")
public class CompressionModule implements StarlarkValue {

  @StarlarkMethod(
      name = "unzip_path",
      doc =
          "DEPRECATED: Use `archive.extract` instead.\n"
              + "Unzip the zipped source CheckoutPath and unzip it to the destination CheckoutPath",
      parameters = {
        @Param(
            name = "source_path",
            doc = "the zipped file source",
            allowedTypes = {@ParamType(type = CheckoutPath.class)}),
        @Param(
            name = "destination_path",
            doc = "the path to unzip to",
            allowedTypes = {@ParamType(type = CheckoutPath.class)}),
        @Param(
            name = "filter",
            named = true,
            allowedTypes = {
              @ParamType(type = Glob.class),
              @ParamType(type = StarlarkList.class, generic1 = String.class),
              @ParamType(type = NoneType.class),
            },
            doc =
                "A glob relative to the archive root that will restrict what files \n"
                    + "from the archive should be extracted.",
            defaultValue = "None",
            positional = false),
      })
  public void unzipPath(CheckoutPath source, CheckoutPath destination, Object filter)
      throws IOException, ValidationException, EvalException {
    Glob filterGlob = Glob.wrapGlob(filter, null);
    InputStream contents = Files.newInputStream(source.fullPath());
    ExtractUtil.extractArchive(contents, destination.fullPath(), ExtractType.ZIP, filterGlob);
  }
}
