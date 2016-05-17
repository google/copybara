// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.testing;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.Destination;
import com.google.copybara.Options;
import com.google.copybara.Origin.Reference;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * A destination for testing which doesn't write the workdir anywhere and simply records when
 * {@link Destination#process(Path, Reference, long, String)} is called and with what arguments.
 */
public class RecordsProcessCallDestination implements Destination, Destination.Yaml {

  public final List<ProcessedChange> processed = new ArrayList<>();

  @Override
  public void process(Path workdir, Reference<?> originRef, long timestamp, String changesSummary) {
    processed.add(new ProcessedChange(originRef, timestamp, changesSummary, copyWorkdir(workdir)));
  }

  private ImmutableMap<String, String> copyWorkdir(final Path workdir) {
    final ImmutableMap.Builder<String, String> result = ImmutableMap.builder();
    try {
      Files.walkFileTree(workdir, new SimpleFileVisitor<Path>() {

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          result.put(workdir.relativize(file).toString(), new String(Files.readAllBytes(file)));
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    return result.build();
  }

  @Nullable
  @Override
  public String getPreviousRef(String labelName) {
    return processed.isEmpty()
        ? null
        : processed.get(processed.size() - 1).originRef.asString();
  }

  @Override
  public Destination withOptions(Options options, String configName) {
    return this;
  }

  public static class ProcessedChange {

    private final long timestamp;
    private final Reference<?> originRef;
    private final String changesSummary;
    private final ImmutableMap<String, String> workdir;

    private ProcessedChange(Reference<?> originRef, long timestamp, String changesSummary,
        ImmutableMap<String, String> workdir) {
      this.timestamp = timestamp;
      this.originRef = originRef;
      this.changesSummary = changesSummary;
      this.workdir = workdir;
    }

    public long getTimestamp() {
      return timestamp;
    }

    public Reference<?> getOriginRef() {
      return originRef;
    }

    public String getChangesSummary() {
      return changesSummary;
    }

    public int numFiles() {
      return workdir.size();
    }

    public String getContent(String fileName) {
      return Preconditions.checkNotNull(
          workdir.get(fileName), "Cannot find content for %s", fileName);
    }

    public boolean filePresent(String fileName) {
      return workdir.containsKey(fileName);
    }
  }
}
