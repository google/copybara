/*
 * Copyright (C) 2022 Google Inc.
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

package com.google.copybara.config;

import com.google.common.base.Preconditions;
import com.google.common.flogger.FluentLogger;
import com.google.copybara.exception.CannotResolveLabel;
import java.io.IOException;

/**
 * A {@link ConfigFile} that delegates to a main config file and falls back to a secondary one for
 * file resolution, if necessary.
 *
 * <p>This is useful for cases where generated in-memory configurations have dependencies on
 * persisted configurations.
 */
public final class ResolveDelegateConfigFile implements ConfigFile {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ConfigFile mainConfigFile;
  private final ConfigFile secondConfigFile;

  public ResolveDelegateConfigFile(ConfigFile mainConfigFile, ConfigFile secondConfigFile) {
    this.mainConfigFile = Preconditions.checkNotNull(mainConfigFile);
    this.secondConfigFile = Preconditions.checkNotNull(secondConfigFile);
  }

  @Override
  public ConfigFile resolve(String path) throws CannotResolveLabel {
    try {
      return mainConfigFile.resolve(path);
    } catch (CannotResolveLabel e) {
      logger.atInfo().log(
          "Could not resolve %s from %s. Resolving from %s.",
          path, mainConfigFile.path(), secondConfigFile.path());
      try {
        return secondConfigFile.resolve(path);
      } catch (CannotResolveLabel crl) {
        throw new CannotResolveLabel(
            String.format(
                "Could not resolve main config or second config to path '%s'. Main config path is"
                    + " '%s', second config path is '%s'",
                path, mainConfigFile.path(), secondConfigFile.path()),
            crl);
      }
    }
  }

  @Override
  public String path() {
    return mainConfigFile.path();
  }

  @Override
  public byte[] readContentBytes() throws IOException, CannotResolveLabel {
    return mainConfigFile.readContentBytes();
  }

  @Override
  public String getIdentifier() {
    return mainConfigFile.getIdentifier();
  }
}
