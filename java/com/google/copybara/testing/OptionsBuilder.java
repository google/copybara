// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.testing;

import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Jimfs;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Option;
import com.google.copybara.Options;
import com.google.copybara.WorkflowNameOptions;
import com.google.copybara.git.GerritOptions;
import com.google.copybara.git.GitOptions;
import com.google.copybara.localdir.LocalDestinationOptions;
import com.google.copybara.util.console.LogConsole;

import java.io.IOException;
import java.nio.file.FileSystems;

/**
 * Allows building complete and sane {@link Options} instances succinctly.
 */
public class OptionsBuilder {

  public GeneralOptions general =
      new GeneralOptions(Jimfs.newFileSystem(), /*verbose=*/true, /*lastRevision=*/
          null, new LogConsole(System.out));
  public LocalDestinationOptions localDestination =
      new LocalDestinationOptions();
  public GitOptions git = new GitOptions();
  public GerritOptions gerrit = new GerritOptions();
  public WorkflowNameOptions workflowName = new WorkflowNameOptions("default");

  public final OptionsBuilder setWorkdirToRealTempDir() throws IOException {
    general = new GeneralOptions(FileSystems.getDefault(), /*verbose=*/true,
        /*lastRevision=*/null, new LogConsole(System.out));
    return this;
  }

  /**
   * Returns all options to include in the built {@link Options} instance. This can be overridden by
   * child classes, in which case it should also include the superclass' instances.
   */
  protected Iterable<Option> allOptions() {
    return ImmutableList.of(general, localDestination, git, gerrit, workflowName);
  }

  public final Options build() {
    return new Options(ImmutableList.copyOf(allOptions()));
  }
}
