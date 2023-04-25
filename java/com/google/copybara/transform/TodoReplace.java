/*
 * Copyright (C) 2017 Google Inc.
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

package com.google.copybara.transform;

import static com.google.copybara.exception.ValidationException.checkCondition;
import static com.google.copybara.transform.TodoReplace.Mode.MAP_OR_FAIL;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.copybara.LocalParallelizer;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.TransformationStatus;
import com.google.copybara.exception.NonReversibleValidationException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.treestate.TreeState.FileState;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.starlark.java.syntax.Location;

/**
 * Map Google style TODOs
 */
public class TodoReplace implements Transformation {

  private static final Pattern SINGLE_USER_PATTERN = Pattern.compile("([ \t]*)([^ \t]*)([ \t]*)");

  private final Pattern pattern;
  private final Location location;
  private final Glob glob;
  private final ImmutableList<String> todoTags;
  private final LocalParallelizer parallelizer;
  private final Mode mode;
  private final ImmutableMap<String, String> mapping;
  @Nullable
  private final String defaultString;
  @Nullable
  private final Pattern regexIgnorelist;

  public TodoReplace(
      Location location,
      Glob glob,
      ImmutableList<String> todoTags,
      Mode mode,
      Map<String, String> mapping,
      @Nullable String defaultString,
      LocalParallelizer parallelizer,
      @Nullable Pattern regexIgnorelist) {
    this.location = Preconditions.checkNotNull(location);
    this.glob = Preconditions.checkNotNull(glob);
    this.todoTags = Preconditions.checkNotNull(todoTags);
    this.parallelizer = parallelizer;
    Preconditions.checkArgument(!todoTags.isEmpty());
    this.mode = Preconditions.checkNotNull(mode);
    this.mapping = Preconditions.checkNotNull(ImmutableMap.copyOf(mapping));
    this.defaultString = defaultString;
    if (mode == Mode.USE_DEFAULT || mode == Mode.MAP_OR_DEFAULT) {
      Preconditions.checkNotNull(defaultString);
    }
    this.regexIgnorelist = regexIgnorelist;
    pattern = createPattern(todoTags);
  }

  private Pattern createPattern(ImmutableList<String> todoTags) {
    return Pattern.compile("((?:"
        + Joiner.on("|").join(todoTags.stream().map(Pattern::quote).collect(Collectors.toList()))
        + ") ?)\\((.*?)\\)");
  }

  @Override
  public TransformationStatus transform(TransformWork work)
      throws IOException, ValidationException {
    work.getTreeState().notifyModify(
        Iterables.concat(
            parallelizer.run(
                work.getTreeState().find(glob.relativeTo(work.getCheckoutDir())),
                files -> run(files, work.getConsole()))));
    return TransformationStatus.success();
  }

  private Set<FileState> run(Iterable<FileState> files, Console console)
      throws IOException, ValidationException {
    Set<FileState> modifiedFiles = new HashSet<>();
    // TODO(malcon): Remove reconstructing pattern once RE2J doesn't synchronize on matching.
    Pattern batchPattern = Pattern.compile(pattern.pattern(), pattern.flags());
    for (FileState file : files) {
      if (Files.isSymbolicLink(file.getPath())) {
        continue;
      }
      String content = new String(Files.readAllBytes(file.getPath()), UTF_8);
      Matcher matcher = batchPattern.matcher(content);
      StringBuffer sb = new StringBuffer();
      boolean modified = false;
      while (matcher.find()) {
        if (matcher.group(2).trim().isEmpty()){
          matcher.appendReplacement(sb, matcher.group(0));
          continue;
        }
        List<String> users = Splitter.on(",").splitToList(matcher.group(2));
        List<String> mappedUsers = mapUsers(users, matcher.group(0), file.getPath(), console);
        modified |= !users.equals(mappedUsers);
        String result = matcher.group(1);
        if (!mappedUsers.isEmpty()) {
          result += "(" + Joiner.on(",").join(mappedUsers) + ")";
        }
        matcher.appendReplacement(sb, Matcher.quoteReplacement(result));
      }
      matcher.appendTail(sb);

      if (modified) {
        modifiedFiles.add(file);
        Files.write(file.getPath(), sb.toString().getBytes(UTF_8));
      }
    }
    return modifiedFiles;
  }

  private List<String> mapUsers(List<String> users, String rawText, Path path, Console console)
      throws ValidationException {
    Set<String> alreadyAdded = new HashSet<>();
    List<String> result = new ArrayList<>();
    for (String rawUser : users) {
      Matcher matcher = SINGLE_USER_PATTERN.matcher(rawUser);
      // Throw VE if the pattern doesn't match and mode is MAP_OR_FAIL
      if (!matcher.matches()) {
        checkCondition(mode != MAP_OR_FAIL,
            "Unexpected '%s' doesn't match expected format", rawUser);
        console.warnFmt("Skipping '%s' that doesn't match expected format", rawUser);
        continue;
      }
      String prefix = matcher.group(1);
      String originUser = matcher.group(2);
      String suffix = matcher.group(3);
      if (regexIgnorelist != null) {
        if (regexIgnorelist.matcher(originUser).matches()) {
          result.add(prefix + originUser + suffix);
          continue;
        }
      }
      switch (mode) {
        case MAP_OR_FAIL:
          checkCondition(mapping.containsKey(originUser),
              "Cannot find a mapping '%s' in '%s' (%s)", originUser, rawText, path);
          // fall through
        case MAP_OR_IGNORE:
          String destUser = mapping.getOrDefault(originUser, originUser);
          if (alreadyAdded.add(destUser)) {
            result.add(prefix + destUser + suffix);
          }
          break;
        case MAP_OR_DEFAULT:
          destUser = mapping.getOrDefault(originUser, defaultString);
          if (alreadyAdded.add(destUser)) {
            result.add(prefix + destUser + suffix);
          }
          break;
        case SCRUB_NAMES:
          break;
        case USE_DEFAULT:
          if (alreadyAdded.add(defaultString)) {
            result.add(prefix + defaultString + suffix);
          }
          break;
      }
    }
    return result;
  }

  @Override
  public Transformation reverse() throws NonReversibleValidationException {
    if (mode != Mode.MAP_OR_FAIL && mode != Mode.MAP_OR_IGNORE) {
      throw new NonReversibleValidationException(mode + " mode is not reversible");
    }

    BiMap<String, String> mapping;
    try {
      mapping = HashBiMap.create(this.mapping);
    } catch (IllegalArgumentException e) {
      throw new NonReversibleValidationException("Non-reversible mapping: " + e.getMessage());
    }

    return new TodoReplace(
        location,
        glob,
        todoTags,
        mode,
        mapping.inverse(),
        defaultString,
        parallelizer,
        regexIgnorelist);
  }

  @Override
  public String describe() {
    return "Replacing " + todoTags;
  }

  @Override
  public Location location() {
    return location;
  }

  /**
   * How to transforms TODOs in code.
   */
  public enum Mode {
    /** Try to use the mapping and if not found fail. */
    MAP_OR_FAIL,
    /** Try to use the mapping but ignore if no mapping found. */
    MAP_OR_IGNORE,
    /** Try to use the mapping and use the default if not found. */
    MAP_OR_DEFAULT,
    /** Scrub all names from TODOs. Transforms 'TODO(foo)' to 'TODO' */
    SCRUB_NAMES,
    /** Replace any TODO(foo, bar) with TODO(default_string) */
    USE_DEFAULT
  }
}
