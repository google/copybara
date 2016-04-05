// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.google.common.truth.Truth;
import com.google.copybara.config.Config;
import com.google.copybara.git.GitDestination;
import com.google.copybara.git.GitOptions;

import com.beust.jcommander.internal.Nullable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@RunWith(JUnit4.class)
public class CopybaraTest {

  @Test
  public void doNothing() throws IOException, RepoException {
    Config.Yaml config = new Config.Yaml();
    config.setName("name");
    config.setOrigin(new Repository.Yaml() {
      @Override
      public Repository withOptions(Options options) {
        return new Repository() {
          @Override
          public void checkoutReference(@Nullable String reference, Path workdir)
              throws RepoException {
            try {
              Files.createDirectories(workdir);
              Files.write(workdir.resolve("file.txt"), reference.getBytes());
            } catch (IOException e) {
              throw new RepoException("Unexpected error", e);
            }
          }
        };
      }
    });
    GitDestination.Yaml destination = new GitDestination.Yaml();
    destination.setUrl("file:///repos/foo");
    destination.setPushToRef("refs/to/master");
    config.setDestination(destination);
    Path workdir = Files.createTempDirectory("workdir");
    Options options = new Options(new GitOptions(), new GeneralOptions());
    new Copybara(workdir).runForSourceRef(config.withOptions(options), "some_sha1");
    Truth.assertThat(Files.readAllLines(workdir.resolve("file.txt"), StandardCharsets.UTF_8))
        .contains("some_sha1");
  }
}
