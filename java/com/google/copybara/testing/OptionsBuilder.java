// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.testing;

import com.google.common.base.StandardSystemProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.jimfs.Jimfs;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Option;
import com.google.copybara.Options;
import com.google.copybara.WorkflowOptions;
import com.google.copybara.folder.FolderDestinationOptions;
import com.google.copybara.git.GerritOptions;
import com.google.copybara.git.GitOptions;
import com.google.copybara.testing.TestingModule.TestingOptions;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.LogConsole;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.util.HashMap;
import java.util.Map;

/**
 * Allows building complete and sane {@link Options} instances succinctly.
 */
public class OptionsBuilder {

  public GeneralOptions general =
      new GeneralOptions(
          System.getenv(),
          Jimfs.newFileSystem(),
          /*verbose=*/true,
          LogConsole.readWriteConsole(System.in, System.out),
          /*skylark=*/
          /*validate=*/false);

  public FolderDestinationOptions localDestination = new FolderDestinationOptions();
  public GitOptions git = new GitOptions(StandardSystemProperty.USER_HOME.value());
  public GerritOptions gerrit = new GerritOptions();
  public WorkflowOptions workflowOptions = new WorkflowOptions(
      /*changeBaseline=*/null, /*lastRevision=*/ null, "default");

  public TestingOptions testingOptions = new TestingOptions();

  public final OptionsBuilder setWorkdirToRealTempDir() throws IOException {
    general = new GeneralOptions(
        updateEnvironment(general.getEnvironment(), "PWD", StandardSystemProperty.USER_DIR.value()),
        FileSystems.getDefault(), /*verbose=*/true,
        LogConsole.readWriteConsole(System.in, System.out));
    return this;
  }

  public final OptionsBuilder setConsole(Console newConsole) {
    general = new GeneralOptions(
        general.getEnvironment(), general.getFileSystem(), general.isVerbose(), newConsole,
        general.isValidate());
    return this;
  }

  public final OptionsBuilder setHomeDir(String homeDir) {
    general = new GeneralOptions(
        updateEnvironment(general.getEnvironment(), "HOME", homeDir),
        general.getFileSystem(), general.isVerbose(), general.console(), general.isValidate());
    git = new GitOptions(homeDir);
    return this;
  }

  public final OptionsBuilder setWorkflowName(String workflowName) {
    workflowOptions = new WorkflowOptions(
        workflowOptions.getChangeBaseline(), workflowOptions.getLastRevision(), workflowName);
    return this;
  }

  public final OptionsBuilder setChangeBaseline(String changeBaseline) {
    workflowOptions = new WorkflowOptions(
        changeBaseline, workflowOptions.getLastRevision(), workflowOptions.getWorkflowName());
    return this;
  }

  public final OptionsBuilder setLastRevision(String lastRevision) {
    workflowOptions = new WorkflowOptions(
        workflowOptions.getChangeBaseline(), lastRevision, workflowOptions.getWorkflowName());
    return this;
  }

  /**
   * Returns all options to include in the built {@link Options} instance. This can be overridden by
   * child classes, in which case it should also include the superclass' instances.
   */
  protected Iterable<Option> allOptions() {
    return ImmutableList
        .of(general, localDestination, git, gerrit, workflowOptions, testingOptions);
  }

  public final Options build() {
    return new Options(ImmutableList.copyOf(allOptions()));
  }

  private static Map<String, String> updateEnvironment(
      Map<String, String> environment, String key, String value) {
    HashMap<String, String> updatedEnvironment = new HashMap<>(environment);
    updatedEnvironment.put(key, value);
    return ImmutableMap.copyOf(updatedEnvironment);
  }
}
