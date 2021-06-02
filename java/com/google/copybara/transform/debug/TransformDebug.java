/*
 * Copyright (C) 2019 Google Inc.
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

package com.google.copybara.transform.debug;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Equivalence;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.MapDifference;
import com.google.common.collect.MapDifference.ValueDifference;
import com.google.common.collect.Maps;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.TransformationStatus;
import com.google.copybara.exception.NonReversibleValidationException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.treestate.TreeState;
import com.google.copybara.treestate.TreeState.FileState;
import com.google.copybara.util.DiffUtil;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.Glob;
import com.google.copybara.util.InsideGitDirException;
import com.google.copybara.util.console.AnsiColor;
import com.google.copybara.util.console.Console;
import com.google.re2j.Pattern;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * A transformation that delegates to another transformation and allows to debug its execution.
 */
public final class TransformDebug implements Transformation {

  private static final String COPYBARA_METADATA_FAKE_FILE =
      " Copybara metadata(Author, description, etc.)";
  private final Transformation delegate;
  private final DebugOptions debugOptions;
  private final Map<String, String> environment;

  private TransformDebug(Transformation delegate, DebugOptions debugOptions,
      Map<String, String> environment) {
    this.delegate = Preconditions.checkNotNull(delegate);
    this.debugOptions = Preconditions.checkNotNull(debugOptions);
    this.environment = Preconditions.checkNotNull(environment);
  }

  @Override
  public TransformationStatus transform(TransformWork work)
      throws IOException, ValidationException, RepoException {
    Console console = work.getConsole();
    boolean fileDebug = false;
    if (debugOptions.getDebugFileBreak() != null) {
      fileDebug = true;
    }
    boolean metadataDebug = false;
    if (debugOptions.debugMetadataBreak) {
      metadataDebug = true;
    }

    Pattern debugTransformBreak = debugOptions.getDebugTransformBreak();
    boolean transformMatch = false;
    if (debugTransformBreak != null
        && debugTransformBreak.matcher(this.delegate.describe())
        .find()) {
      transformMatch = true;
    }

    if (!fileDebug && !metadataDebug && !transformMatch) {
      // Nothing to debug!
      return delegate.transform(work);
    }

    TreeMap<String, byte[]> before = readState(work, fileDebug || transformMatch,
        work.getTreeState());
    TransformationStatus status = delegate.transform(work);
    work.validateTreeStateCache();
    TreeMap<String, byte[]> after = readState(work, fileDebug || transformMatch,
        work.getTreeState());

    MapDifference<String, byte[]> difference = Maps.difference(before, after,
        new Equivalence<byte[]>() {
          @Override
          protected boolean doEquivalent(byte[] one, byte[] other) {
            return Arrays.equals(one, other);
          }

          @Override
          protected int doHash(byte[] bytes) {
            return Arrays.hashCode(bytes);
          }
        });

    boolean stop = transformMatch;
    if (fileDebug) {
      PathMatcher debugFileBreak = debugOptions.getDebugFileBreak()
          .relativeTo(Paths.get("/"));
      for (String path : Iterables.concat(difference.entriesOnlyOnLeft().keySet(),
          difference.entriesOnlyOnRight().keySet(), difference.entriesDiffering().keySet())) {
        if (path.equals(COPYBARA_METADATA_FAKE_FILE)) {
          continue;
        }
        if (debugFileBreak.matches(Paths.get("/" + path))) {
          stop = true;
          console.infoFmt("File '%s' change matched. Stopping", path);
          break;
        }
      }
    } else if (metadataDebug && !Arrays.equals(
          before.get(COPYBARA_METADATA_FAKE_FILE),
          after.get(COPYBARA_METADATA_FAKE_FILE))) {
      stop = true;
      console.infoFmt("Message, author and/or labels changed");
    }
    if (!stop) {
      return status;
    }
    if (!transformMatch) {
      // Stopped because of file/metadata change. Show the diff directly
      showDiff(console, difference);
    }
    while (true) {
      String answer = console.ask(
          "Debugger stopped after '" + delegate.describe()
              + "' "
              + console.colorize(AnsiColor.PURPLE, delegate.location().toString())
              + ".\n"
              + "      Current file state can be checked at " + work.getCheckoutDir() +"\n"
              + "Diff (d), Continue (c), Stop (s): ",
          "d",
          input -> ImmutableSet.of("d", "c", "s").contains(input));

      switch (answer) {
        case "d": {
          showDiff(console, difference);
          break;
        }
        case "c":
          return status;
        case "s":
          throw new ValidationException("Stopped by user");
      }
    }
  }

  private void showDiff(Console console, MapDifference<String, byte[]> difference)
      throws IOException, ValidationException {
    if (difference.areEqual()) {
      console.info("No changes detected");
      return;
    }
    Path debug = debugOptions.createDiffDirectory();
    FileUtil.deleteRecursively(debug);
    Files.createDirectory(debug);
    Path beforePath = debug.resolve("before");
    Files.createDirectory(beforePath);
    Path afterPath = debug.resolve("after");
    Files.createDirectory(afterPath);

    for (Entry<String, byte[]> entry : difference.entriesOnlyOnLeft().entrySet()) {
      writeFile(beforePath, entry.getKey(), entry.getValue());
    }
    for (Entry<String, byte[]> entry : difference.entriesOnlyOnRight().entrySet()) {
      writeFile(afterPath, entry.getKey(), entry.getValue());
    }
    for (Entry<String, ValueDifference<byte[]>> entry : difference.entriesDiffering()
        .entrySet()) {
      writeFile(beforePath, entry.getKey(), entry.getValue().leftValue());
      writeFile(afterPath, entry.getKey(), entry.getValue().rightValue());
    }

    try {
      console.info(DiffUtil.colorize(console,
          new String(DiffUtil.diff(beforePath, afterPath, /*verbose=*/ false,
              environment), UTF_8)));

    } catch (InsideGitDirException e) {
      throw new ValidationException("Cannot debug if temporary directory is inside"
          + " a git directory", e);
    }
  }

  private void writeFile(Path basePath, String path, byte[] content) throws IOException {
    Files.createDirectories(basePath.resolve(path).getParent());
    Files.write(basePath.resolve(path), content);
  }

  private TreeMap<String, byte[]> readState(TransformWork work, boolean filesNeeded,
      TreeState treeState)
      throws IOException {
    TreeMap<String, byte[]> result = new TreeMap<>();

    result.put(COPYBARA_METADATA_FAKE_FILE, work.getMetadata().toString().getBytes(UTF_8));

    if (filesNeeded) {
      Iterable<FileState> files = treeState.find(
          Glob.ALL_FILES.relativeTo(work.getCheckoutDir()));

      for (FileState beforeFile : files) {
        Path relative = work.getCheckoutDir().relativize(beforeFile.getPath());
        byte[] bytes = Files.readAllBytes(beforeFile.getPath());
        result.put(relative.toString(),
            Files.size(beforeFile.getPath()) > 100_000
                ? ("File too big. Hash: " + BaseEncoding.base16().encode(
                Hashing.goodFastHash(32).hashBytes(bytes).asBytes())).getBytes(UTF_8)
                : Files.readAllBytes(beforeFile.getPath()));
      }
    }
    return result;
  }

  /**
   * Returns the inner transformation
   */
  public Transformation getDelegate() {
    return delegate;
  }

  @Override
  public Transformation reverse() throws NonReversibleValidationException {
    return new TransformDebug(delegate.reverse(), debugOptions, environment);
  }

  @Override
  public String describe() {
    return delegate.describe();
  }

  @Override
  public boolean canJoin(Transformation transformation) {
    return false;
  }

  @Override
  public Transformation join(Transformation next) {
    throw new IllegalStateException(
        "Debugger doesn't support join!: delegate = " + delegate + ", next = " + next);
  }

  static Transformation withDebugger(Transformation t,
      DebugOptions debugOptions, Map<String, String> environment) {
    return t instanceof TransformDebug ? t : new TransformDebug(t, debugOptions,
        environment);
  }
}
