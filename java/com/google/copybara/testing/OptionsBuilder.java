// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.testing;

import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Jimfs;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.git.GerritOptions;
import com.google.copybara.git.GitOptions;
import com.google.copybara.localdir.LocalDestinationOptions;

import java.io.IOException;
import java.nio.file.Files;

/**
 * Allows building complete and sane {@link Options} instances succinctly.
 */
public final class OptionsBuilder {

  public GeneralOptions general =
      new GeneralOptions(Jimfs.newFileSystem().getPath("/"), /*verbose=*/true, /*lastRevision=*/
          null);
  public LocalDestinationOptions localDestination =
      new LocalDestinationOptions();
  public GitOptions git = new GitOptions();
  public GerritOptions gerrit = new GerritOptions();

  public OptionsBuilder setWorkdirToRealTempDir() throws IOException {
    general = new GeneralOptions(Files.createTempDirectory("OptionsBuilder"), /*verbose=*/true,
        /*lastRevision=*/null);
    return this;
  }

  public Options build() {
    return new Options(ImmutableList.of(general, localDestination, git, gerrit));
  }
}
