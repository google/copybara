// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.google.common.truth.Truth;
import com.google.copybara.config.Config;

import com.beust.jcommander.internal.Nullable;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class CopybaraTest {

  @Test
  public void doNothing() throws IOException, RepoException {
    Config.Yaml config = new Config.Yaml();
    config.setName("name");
    config.setSourceOfTruth(new Repository.Yaml() {
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
    config.setDestinationPath("src/copybara");
    Path workdir = Files.createTempDirectory("workdir");
    new Copybara(workdir).runForSourceRef(config.withOptions(new Options()), "some_sha1");
    Truth.assertThat(Files.readAllLines(workdir.resolve("file.txt"), StandardCharsets.UTF_8))
        .contains("some_sha1");
  }
}
