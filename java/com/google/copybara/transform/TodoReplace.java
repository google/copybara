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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.NonReversibleValidationException;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.ValidationException;
import com.google.copybara.treestate.TreeState.FileState;
import com.google.copybara.util.Glob;
import com.google.devtools.build.lib.events.Location;
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

/**
 * Map Google style TODOs
 */
public class TodoReplace implements Transformation {

  private static final Pattern SINGLE_USER_PATTERN = Pattern.compile("([ \t]*)([^ \t]*)([ \t]*)");

  private final Pattern pattern;
  private Location location;
  private Glob glob;
  private ImmutableList<String> todoTags;
  private Mode mode;
  private ImmutableMap<String, String> mapping;
  @Nullable
  private String defaultString;

  public TodoReplace(Location location, Glob glob, ImmutableList<String> todoTags,
      Mode mode,
      Map<String, String> mapping, @Nullable String defaultString) {
    this.location = Preconditions.checkNotNull(location);
    this.glob = Preconditions.checkNotNull(glob);
    this.todoTags = Preconditions.checkNotNull(todoTags);
    Preconditions.checkArgument(!todoTags.isEmpty());
    this.mode = Preconditions.checkNotNull(mode);
    this.mapping = Preconditions.checkNotNull(ImmutableMap.copyOf(mapping));
    this.defaultString = defaultString;
    if (mode == Mode.USE_DEFAULT || mode == Mode.MAP_OR_DEFAULT) {
      Preconditions.checkNotNull(defaultString);
    }
    pattern = createPattern(todoTags);
  }

  private Pattern createPattern(ImmutableList<String> todoTags) {
    return Pattern.compile("((?:"
        + Joiner.on("|").join(todoTags.stream().map(Pattern::quote).collect(Collectors.toList()))
        + ") ?)\\((.*?)\\)");
  }

  @Override
  public void transform(TransformWork work) throws IOException, ValidationException {
    Path checkoutDir = work.getCheckoutDir();
    Iterable<FileState> files = work.getTreeState().find(glob.relativeTo(checkoutDir));

    Set<FileState> modifiedFiles = new HashSet<>();
    for (FileState file : files) {
      if (Files.isSymbolicLink(file.getPath())) {
        continue;
      }
      String content = new String(Files.readAllBytes(file.getPath()), UTF_8);
      Matcher matcher = pattern.matcher(content);
      StringBuffer sb = new StringBuffer();
      boolean modified = false;
      while (matcher.find()) {
        List<String> users = Splitter.on(",").splitToList(matcher.group(2));
        List<String> mappedUsers = mapUsers(users, matcher.group(0), file.getPath());
        modified |= !users.equals(mappedUsers);
        String result = matcher.group(1);
        if (!mappedUsers.isEmpty()) {
          result += "(" + Joiner.on(",").join(mappedUsers) + ")";
        }
        matcher.appendReplacement(sb, result);
      }
      matcher.appendTail(sb);

      if (modified) {
        modifiedFiles.add(file);
        Files.write(file.getPath(), sb.toString().getBytes(UTF_8));
      }
    }
    work.getTreeState().notifyModify(modifiedFiles);
  }

  private List<String> mapUsers(List<String> users, String rawText, Path path)
      throws ValidationException {
    Set<String> alreadAdded = new HashSet<>();
    List<String> result = new ArrayList<>();
    for (String rawUser : users) {
      Matcher matcher = SINGLE_USER_PATTERN.matcher(rawUser);
      // Regex is pretty lax. It should match.
      Preconditions.checkState(matcher.matches(), rawUser);
      String prefix = matcher.group(1);
      String originUser = matcher.group(2);
      String suffix = matcher.group(3);
      switch (mode) {
        case MAP_OR_FAIL:
          ValidationException.checkCondition(mapping.containsKey(originUser),
              String.format("Cannot find a mapping '%s' in '%s' (%s)", originUser, rawText, path));
          // fall through
        case MAP_OR_IGNORE:
          String destUser = mapping.getOrDefault(originUser, originUser);
          if (alreadAdded.add(destUser)) {
            result.add(prefix + destUser + suffix);
          }
          break;
        case MAP_OR_DEFAULT:
          destUser = mapping.getOrDefault(originUser, defaultString);
          if (alreadAdded.add(destUser)) {
            result.add(prefix + destUser + suffix);
          }
          break;
        case SCRUB_NAMES:
          break;
        case USE_DEFAULT:
          if (alreadAdded.add(defaultString)) {
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
      throw new NonReversibleValidationException(location, mode + " mode is not reversible");
    }

    BiMap<String, String> mapping;
    try {
      mapping = HashBiMap.create(this.mapping);
    } catch (IllegalArgumentException e) {
      throw new NonReversibleValidationException(location,
          "Non-reversible mapping: " + e.getMessage());
    }

    return new TodoReplace(location, glob, todoTags, mode, mapping.inverse(), defaultString);
  }

  @Override
  public String describe() {
    return "Replacing " + todoTags;
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
