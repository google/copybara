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

package com.google.copybara.credentials;

import com.google.common.collect.ImmutableSetMultimap;
import java.io.IOException;
import java.nio.file.Path;
import javax.annotation.Nullable;
import net.starlark.java.eval.StarlarkValue;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

/** Fetches a value located within a toml file. */
public class TomlKeySource implements CredentialIssuer, StarlarkValue {

  Path file;
  String dotPath;

  public TomlKeySource(Path file, String keyPath) {
    this.file = file;
    this.dotPath = keyPath;
  }

  @Override
  public Credential issue() throws CredentialIssuingException {
    TomlParseResult tomlParseResult = null;
    try {
      tomlParseResult = Toml.parse(file);
    } catch (IOException e) {
      throw new CredentialIssuingException("Error reading Toml file.", e);
    }
    @Nullable String data = tomlParseResult.getString(dotPath);
    if (data == null) {
      throw new CredentialIssuingException(
          String.format("key %s not found in file %s", dotPath, file));
    }
    return new StaticSecret(dotPath, data);
  }

  @Override
  public ImmutableSetMultimap<String, String> describe() {
    return ImmutableSetMultimap.of("type", "Toml", "dotPath", dotPath);
  }
}
