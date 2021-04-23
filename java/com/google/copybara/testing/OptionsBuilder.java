/*
 * Copyright (C) 2016 Google Inc.
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

package com.google.copybara.testing;

import com.google.api.client.http.HttpTransport;
import com.google.common.base.StandardSystemProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.jimfs.Jimfs;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Option;
import com.google.copybara.Options;
import com.google.copybara.WorkflowOptions;
import com.google.copybara.buildozer.BuildozerOptions;
import com.google.copybara.folder.FolderDestinationOptions;
import com.google.copybara.folder.FolderOriginOptions;
import com.google.copybara.format.BuildifierOptions;
import com.google.copybara.git.GerritOptions;
import com.google.copybara.git.GitDestinationOptions;
import com.google.copybara.git.GitHubDestinationOptions;
import com.google.copybara.git.GitHubOptions;
import com.google.copybara.git.GitHubPrOriginOptions;
import com.google.copybara.git.GitMirrorOptions;
import com.google.copybara.git.GitOptions;
import com.google.copybara.git.GitOriginOptions;
import com.google.copybara.hg.HgOptions;
import com.google.copybara.hg.HgOriginOptions;
import com.google.copybara.remotefile.RemoteFileOptions;
import com.google.copybara.testing.TestingModule.TestingOptions;
import com.google.copybara.transform.debug.DebugOptions;
import com.google.copybara.transform.patch.PatchingOptions;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.testing.TestingConsole;
import java.nio.file.FileSystems;
import java.util.HashMap;
import java.util.Map;

/**
 * Allows building complete and sane {@link Options} instances succinctly.
 */
public class OptionsBuilder {

  public final GeneralOptions general =
      new GeneralOptions(
          System.getenv(),
          Jimfs.newFileSystem(),
          /*verbose=*/ true,
          new TestingConsole(),
          /* configRoot= */ null,
          /*outputRoot*/ null,
          /*reuseOutputDirs*/ true,
          /* disableReversibleCheck= */ false,
          /*force=*/ false, /*outputLimit*/ 0);

  public FolderDestinationOptions folderDestination = new FolderDestinationOptions();
  public FolderOriginOptions folderOrigin = new FolderOriginOptions();

  public GitOptions git = new GitOptions(general);
  public GitOriginOptions gitOrigin = new GitOriginOptions();
  public GitHubPrOriginOptions githubPrOrigin = new GitHubPrOriginOptions();
  public GitDestinationOptions gitDestination = new GitDestinationOptions(general, git);
  public PatchingOptions patch = new PatchingOptions(general);
  public DebugOptions debug = new DebugOptions(general);
  public RemoteFileOptions remoteFile = new RemoteFileOptions();
  public BuildifierOptions buildifier = new BuildifierOptions();

  public String buildozerBin = null;

  public GitHubOptions github = new GitHubOptions(general, git) {
    @Override
    protected HttpTransport newHttpTransport() {
      throw new UnsupportedOperationException(
          "You probably have overwritten GitOptions, so you need to create this variable too");
    }
  };
  public GitHubDestinationOptions githubDestination = new GitHubDestinationOptions();
  public GitMirrorOptions gitMirrorOptions = new GitMirrorOptions();
  public GerritOptions gerrit = new GerritOptions(general, git);
  public WorkflowOptions workflowOptions =
      new WorkflowOptions(/*changeBaseline=*/null, /*lastRevision=*/ null,
          /*checkLastRevState=*/false);

  public HgOptions hg = new HgOptions(general);
  public HgOriginOptions hgOrigin = new HgOriginOptions();

  public TestingOptions testingOptions = new TestingOptions();

  public final OptionsBuilder setWorkdirToRealTempDir() {
    return setWorkdirToRealTempDir(StandardSystemProperty.USER_DIR.value());
  }

  public OptionsBuilder setWorkdirToRealTempDir(String cwd) {
    general.setFileSystemForTest(FileSystems.getDefault());
    general.setEnvironmentForTest(updateEnvironment(general.getEnvironment(), "PWD", cwd));
    return this;
  }

  public OptionsBuilder setEnvironment(Map<String, String> environment) {
    general.setEnvironmentForTest(environment);
    return this;
  }

  public OptionsBuilder setOutputRootToTmpDir() {
    // Using Files.createTempDirectory() generates paths > 255 in some tests and that causes
    // 'File name too long' exceptions in Linux
    general.setOutputRootPathForTest(
        FileSystems.getDefault().getPath(StandardSystemProperty.JAVA_IO_TMPDIR.value()));
    return this;
  }

  public final OptionsBuilder setConsole(Console newConsole) {
    general.setConsoleForTest(newConsole);
    return this;
  }

  public final OptionsBuilder setHomeDir(String homeDir) {
    general.setEnvironmentForTest(updateEnvironment(general.getEnvironment(), "HOME", homeDir));
    return this;
  }

  public final OptionsBuilder setForce(boolean force) {
    general.setForceForTest(force);
    return this;
  }

  public final OptionsBuilder setLabels(ImmutableMap<String, String> labels) {
    general.setCliLabelsForTest(labels);
    return this;
  }

  public final OptionsBuilder setLastRevision(String lastRevision) {
    workflowOptions = new WorkflowOptions(workflowOptions.getChangeBaseline(), lastRevision,
        workflowOptions.checkLastRevState);
    return this;
  }

  /**
   * Returns all options to include in the built {@link Options} instance. This can be overridden by
   * child classes, in which case it should also include the superclass' instances.
   */
  protected Iterable<Option> allOptions() {
    BuildozerOptions buildozer = new BuildozerOptions(general, buildifier, workflowOptions);

    if (buildozerBin != null) {
      buildozer.buildozerBin = buildozerBin;
    }
    return ImmutableList
        .of(general, folderDestination, folderOrigin, git, gitOrigin, githubPrOrigin,
            gitDestination, gitMirrorOptions, gerrit, github, githubDestination, hg, hgOrigin,
            workflowOptions, testingOptions, patch, debug, remoteFile, buildifier, buildozer);
  }

  public final Options build() {
    return new Options(ImmutableList.copyOf(allOptions()));
  }

  private static ImmutableMap<String, String> updateEnvironment(
      Map<String, String> environment, String key, String value) {
    HashMap<String, String> updatedEnvironment = new HashMap<>(environment);
    updatedEnvironment.put(key, value);
    return ImmutableMap.copyOf(updatedEnvironment);
  }

}
