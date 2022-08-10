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

package com.google.copybara.version;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.templatetoken.RegexTemplateTokens;
import com.google.copybara.util.console.Console;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;
import javax.annotation.Nullable;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Printer;
import net.starlark.java.syntax.Location;

/**
 * Given a {@link VersionList} and a regex template, finds the latest version that matches
 * the regex.
 */
public class LatestVersionSelector implements VersionSelector {

  private final String format;
  private final TreeMap<Integer, VersionElementType> groupTypes;
  private final RegexTemplateTokens template;

  public LatestVersionSelector(
      String format, Map<String, Pattern> groups, TreeMap<Integer, VersionElementType> groupTypes,
      Location location)
      throws EvalException {
    this.format = format;
    this.groupTypes = checkNotNull(groupTypes);
    template = new RegexTemplateTokens(checkNotNull(format), groups, true, location);
  }

  @Override
  public void repr(Printer printer) {
    printer.append(toString());
  }

  public enum VersionElementType {
    NUMERIC {
      @Override
      public String varName(int idx) {
        return "n" + idx;
      }

      @Override
      public Comparable<?> convert(String val) {
        return Integer.parseInt(val);
      }
    },
    ALPHABETIC {
      @Override
      public String varName(int idx) {
        return "s" + idx;
      }

      @Override
      public Comparable<?> convert(String val) {
        return val;
      }
    };

    public String varName(int idx) {
      return (this == NUMERIC ? "n" : "s") + idx;
    }

    public abstract Comparable<?> convert(String val);
  }

  @Override
  public ImmutableSet<SearchPattern> searchPatterns() {
    return ImmutableSet.of(new SearchPattern(template.getTokens()));
  }

  @Override
  public Optional<String> select(VersionList versionList, @Nullable String requestedRef,
      Console console)
      throws ValidationException, RepoException {
    ImmutableSet<String> refs = versionList.list();

    ImmutableListMultimap<String, Integer> groupIndexes = template.getGroupIndexes();
    List<Object> latest = new ArrayList<>();
    String latestRef = null;
    for (String ref : refs) {
      Matcher matcher = template.getBefore().matcher(ref);
      if (!matcher.matches()) {
        continue;
      }
      List<Object> objs = new ArrayList<>();
      for (Entry<Integer, VersionElementType> groups : groupTypes
          .entrySet()) {
        String var = groups.getValue().varName(groups.getKey());
        String val = matcher.group(Iterables.getLast(groupIndexes.get(var)));
        objs.add(groups.getValue().convert(val));
      }
      if (isAfter(latest, objs)) {
        latest = objs;
        latestRef = ref;
      }
    }
    if (latestRef == null) {
      console.warnFmt("version_selector didn't match any version for '%s'",
          template.getBefore().pattern());
    }

    return Optional.ofNullable(latestRef);
  }

  private boolean isAfter(List<Object> old, List<Object> newer) {
    if (old.isEmpty()) {
      return true;
    }
    Preconditions.checkArgument(old.size() == newer.size());
    for (int i = 0; i < old.size(); i++) {
      int comp = compareElement(old.get(i), newer.get(i));
      switch (comp) {
        case -1:
          return true;
        case 1:
          return false;
        case 0:
          continue;
        default:
          throw new IllegalStateException("Bad compareTo result: " + comp);
      }
    }
    return false; // Everything equal
  }

  @SuppressWarnings("unchecked")
  private int compareElement(Object o, Object n) {
    return ((Comparable) o).compareTo(n);

  }

  public ImmutableList<String> getUnmatchedGroups() {
    Collection<String> usedGroups = template.getGroupIndexes().keySet();
    return groupTypes.entrySet().stream()
        .map(e -> e.getValue().varName(e.getKey()))
        .filter(s -> !usedGroups.contains(s))
        .collect(toImmutableList());
  }

  @Override
  public String toString() {
    return String.format("core.latest_version(format = '%s')", format);
  }
}
