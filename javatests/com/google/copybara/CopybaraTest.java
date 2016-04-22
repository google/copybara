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
    long beginTime = System.currentTimeMillis() / 1000;

    @Override
    public Destination withOptions(Options options) {
      return new Destination() {
        @Override
        public void process(Path workdir, String originRef, long timestamp) {
          timesProcessed++;
          Truth.assertThat(timestamp).isAtLeast(beginTime);
          Truth.assertThat(timestamp).isAtMost(System.currentTimeMillis() / 1000);
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
  public void doNothing() throws Exception {
    Config.Yaml config = new Config.Yaml();
    config.setName("name");
    config.setOrigin(new Origin.Yaml<DummyOrigin>() {
      @Override
      public DummyOrigin withOptions(Options options) {
        return new DummyOrigin();
      }
    });

    // Use a destination that only increments a counter when it is written.
    CountTimesProcessedDestination destination = new CountTimesProcessedDestination();
    config.setDestination(destination);
    Path workdir = Files.createTempDirectory("workdir");
    Options options = new Options(
        ImmutableList.of(new GitOptions(), new GeneralOptions(workdir, /*verbose=*/true)));
    new Copybara(workdir).runForSourceRef(config.withOptions(options), "some_sha1");
    Truth.assertThat(Files.readAllLines(workdir.resolve("file.txt"), StandardCharsets.UTF_8))
        .contains("some_sha1");
    Truth.assertThat(destination.timesProcessed).isEqualTo(1);
  }

  private static class DummyOrigin implements Origin<DummyOrigin> {

    @Override
    public Reference<DummyOrigin> resolve(@Nullable final String reference) {
      return new Reference<DummyOrigin>() {
        @Override
        public Long readTimestamp() {
          return null;
        }

        @Override
        public void checkout(Path workdir) throws RepoException {
          try {
            Files.createDirectories(workdir);
            Files.write(workdir.resolve("file.txt"), reference.getBytes());
          } catch (IOException e) {
            throw new RepoException("Unexpected error", e);
          }
        }

        @Override
        public String asString() {
          return reference;
        }
      };
    }

    @Override
    public ImmutableList<Change<DummyOrigin>> changes(Reference<DummyOrigin> oldRef,
        @Nullable Reference<DummyOrigin> newRef) throws RepoException {
      throw new CannotComputeChangesException("not supported");
    }
  }
}
