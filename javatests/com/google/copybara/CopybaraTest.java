// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import com.google.copybara.config.Config;
import com.google.copybara.git.GitOptions;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

@RunWith(JUnit4.class)
public class CopybaraTest {

  private final static class RecordsProcessCallDestination implements Destination.Yaml {
    List<Long> processTimestamps = new ArrayList<>();

    @Override
    public Destination withOptions(Options options) {
      return new Destination() {
        @Override
        public void process(Path workdir, String originRef, long timestamp, String changesSummary) {
          processTimestamps.add(timestamp);
        }

        @Nullable
        @Override
        public String getPreviousRef() {
          return null;
        }
      };
    }
  }

  private class DummyReference implements Origin.Reference<DummyOrigin> {
    String reference;

    @Override
    public Long readTimestamp() {
      return referenceToTimestamp.get(reference);
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
  }

  private class DummyOrigin implements Origin<DummyOrigin> {
    @Override
    public Reference<DummyOrigin> resolve(@Nullable final String reference) {
      DummyReference wrappedReference = new DummyReference();
      wrappedReference.reference = reference;
      return wrappedReference;
    }

    @Override
    public ImmutableList<Change<DummyOrigin>> changes(Reference<DummyOrigin> oldRef,
        @Nullable Reference<DummyOrigin> newRef) throws RepoException {
      throw new CannotComputeChangesException("not supported");
    }
  }

  private Map<String, Long> referenceToTimestamp;
  private RecordsProcessCallDestination destination;
  private Config.Yaml yaml;
  private Path workdir;

  @Before
  public void setup() throws Exception {
    referenceToTimestamp = new HashMap<>();
    destination = new RecordsProcessCallDestination();
    yaml = new Config.Yaml();
    yaml.setName("name");
    yaml.setOrigin(new Origin.Yaml<DummyOrigin>() {
      @Override
      public DummyOrigin withOptions(Options options) {
        return new DummyOrigin();
      }
    });

    yaml.setDestination(destination);
    workdir = Files.createTempDirectory("workdir");
  }

  private Options options() {
    return new Options(
        ImmutableList.of(new GitOptions(), new GeneralOptions(workdir, /*verbose=*/true)));
  }


  @Test
  public void processIsCalledWithCurrentTimeIfTimestampNotInOrigin() throws Exception {
    long beginTime = System.currentTimeMillis() / 1000;

    new Copybara(workdir).runForSourceRef(yaml.withOptions(options()), "some_sha1");

    long timestamp = destination.processTimestamps.get(0);
    Truth.assertThat(timestamp).isAtLeast(beginTime);
    Truth.assertThat(timestamp).isAtMost(System.currentTimeMillis() / 1000);
  }

  @Test
  public void processIsCalledWithCorrectWorkdir() throws Exception {
    new Copybara(workdir).runForSourceRef(yaml.withOptions(options()), "some_sha1");
    Truth.assertThat(Files.readAllLines(workdir.resolve("file.txt"), StandardCharsets.UTF_8))
        .contains("some_sha1");
  }

  @Test
  public void sendsOriginTimestampToDest() throws Exception {
    referenceToTimestamp.put("refname", (long) 42918273);
    new Copybara(workdir).runForSourceRef(yaml.withOptions(options()), "refname");
    Truth.assertThat(destination.processTimestamps.get(0))
        .isEqualTo(42918273);
  }
}
