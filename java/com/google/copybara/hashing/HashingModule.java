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

package com.google.copybara.hashing;

import static com.google.copybara.config.SkylarkUtil.convertStringList;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.MoreFiles;
import com.google.copybara.CheckoutPath;
import com.google.copybara.exception.ValidationException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.StarlarkValue;

/** Hashing utilities */
@StarlarkBuiltin(name = "hashing", doc = "utilities for hashing")
public class HashingModule implements StarlarkValue {
  @StarlarkMethod(
      name = "path_md5_sum",
      doc =
          "Return the md5 hash of a file at a checkout path. Do not use unless working with legacy"
              + " systems that require MD5.\n"
              + "WARNING: do not use unless working with legacy systems that require MD5",
      parameters = {@Param(name = "path", doc = "checkout path pointing to a file to be hashed")})
  public String pathMd5Sum(CheckoutPath path) throws IOException {
    @SuppressWarnings("deprecation") // added for pypi packaging (in conjunction with other sums)
    HashFunction hashFunc = Hashing.md5();
    return hashFile(path.fullPath(), hashFunc);
  }

  @StarlarkMethod(
      name = "path_sha256_sum",
      doc = "Return the sha256 hash of a file at a checkout path",
      parameters = {@Param(name = "path", doc = "checkout path pointing to a file to be hashed")})
  public String pathSha256Sum(CheckoutPath path) throws IOException {
    return hashFile(path.fullPath(), Hashing.sha256());
  }

  @StarlarkMethod(
      name = "str_sha256_sum",
      doc = "Return the hash of a list of objects based on the algorithm specified",
      parameters = {
        @Param(
            name = "input",
            named = true,
            doc = "One or more string inputs to hash.",
            allowedTypes = {
              @ParamType(type = Sequence.class, generic1 = String.class),
              @ParamType(type = String.class)
            })
      })
  public String hashStringWithSha256(Object input) throws ValidationException, EvalException {
    if (input instanceof String) {
      return Hashing.sha256().hashString((String) input, UTF_8).toString();
    }

    List<String> stringInputs = convertStringList(input, "input");
    Hasher hasher = Hashing.sha256().newHasher();
    if (stringInputs.isEmpty()) {
      throw new ValidationException(
          "hashing.hash_str_with_sha256 cannot be called with an empty object list.");
    }
    for (String stringInput : stringInputs) {
      hasher.putString(stringInput, UTF_8);
    }
    return hasher.hash().toString();
  }

  private String hashFile(Path hashPath, HashFunction hashFunc) throws IOException {
    return MoreFiles.asByteSource(hashPath).hash(hashFunc).toString();
  }
}
