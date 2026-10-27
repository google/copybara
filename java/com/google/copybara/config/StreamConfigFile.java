/*
 * Copyright (C) 2026 Google LLC
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
import com.google.copybara.exception.CannotResolveLabel;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * A {@link ConfigFile} that reads its content from an {@link InputStream} and delegates relative
 * path resolution to a fallback {@link ConfigFile}.
 */
public final class StreamConfigFile implements ConfigFile {

  private final Supplier<InputStream> streamSupplier;
  private final ConfigFile resolveDelegate;
  private final String streamName;
  @Nullable private byte[] contentBytes;

  public StreamConfigFile(
      Supplier<InputStream> streamSupplier, ConfigFile resolveDelegate, String streamName) {
    this.streamSupplier = Preconditions.checkNotNull(streamSupplier);
    this.resolveDelegate = Preconditions.checkNotNull(resolveDelegate);
    this.streamName = Preconditions.checkNotNull(streamName);
  }

  @Override
  public ConfigFile resolve(String path) throws CannotResolveLabel {
    return resolveDelegate.resolve(path);
  }

  @Override
  public String path() {
    return String.format("<%s>/copy.bara.sky", streamName);
  }

  @Override
  public synchronized byte[] readContentBytes() throws IOException {
    if (contentBytes == null) {
      contentBytes = streamSupplier.get().readAllBytes();
    }
    return contentBytes;
  }

  @Override
  public String getIdentifier() {
    return path();
  }
}
