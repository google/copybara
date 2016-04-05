// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import java.io.IOException;
import java.nio.file.Path;

/**
 * A repository which a source of truth can be copied to.
 */
public interface Destination {
  /**
   * Writes the fully-transformed repository stored at {@code workdir} to this destination.
   */
  void process(Path workdir) throws RepoException, IOException;

  interface Yaml {
    Destination withOptions(Options options);
  }
}
