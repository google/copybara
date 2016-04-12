// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import com.google.copybara.config.Config;
import com.google.copybara.git.GitOptions;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.annotation.Nullable;

@RunWith(JUnit4.class)
public class CopybaraTest {

  private final static class CountTimesProcessedDestination implements Destination.Yaml {
    int timesProcessed;

    @Override
    public Destination withOptions(Options options) {
      return new Destination() {
        @Override
        public void process(Path workdir) {
          timesProcessed++;
        }

        @Nullable
        @Override
        public String getPreviousRef() {
          return null;
        }
      };
    }
  }

  @Test
  public void doNothing() throws IOException, RepoException {
    Config.Yaml config = new Config.Yaml();
    config.setName("name");
    config.setOrigin(new Origin.Yaml() {
      @Override
      public Origin withOptions(Options options) {
        return new Origin() {
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

          @Override
          public ImmutableList<Change> changes(String oldRef, @Nullable String newRef)
              throws RepoException {
            throw new CannotComputeChangesException("not supported");
          }
        };
      }
    });

    // Use a destination that only increments a counter when it is written.
    CountTimesProcessedDestination destination = new CountTimesProcessedDestination();
    config.setDestination(destination);
    Path workdir = Files.createTempDirectory("workdir");
    Options options = new Options(ImmutableList.of(new GitOptions(), new GeneralOptions()));
    new Copybara(workdir).runForSourceRef(config.withOptions(options), "some_sha1");
    Truth.assertThat(Files.readAllLines(workdir.resolve("file.txt"), StandardCharsets.UTF_8))
        .contains("some_sha1");
    Truth.assertThat(destination.timesProcessed).isEqualTo(1);
  }
}
