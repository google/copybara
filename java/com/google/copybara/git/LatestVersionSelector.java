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

package com.google.copybara.git;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.copybara.exception.ValidationException.checkCondition;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Iterables;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.transform.RegexTemplateTokens;
import com.google.copybara.util.console.Console;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import javax.annotation.Nullable;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Printer;
import net.starlark.java.syntax.Location;

public class LatestVersionSelector implements VersionSelector {

  private final String refspec;
  private final TreeMap<Integer, VersionElementType> groupTypes;
  private final RegexTemplateTokens template;

  LatestVersionSelector(
      String refspec, Map<String, Pattern> groups, TreeMap<Integer, VersionElementType> groupTypes,
      Location location)
      throws EvalException {
    this.refspec = Preconditions.checkNotNull(refspec);
    this.groupTypes = Preconditions.checkNotNull(groupTypes);
    template = new RegexTemplateTokens(refspec, groups, true, location);
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
  public String asGitRefspec() {
    return refspec.replaceAll("\\$\\{.*}", "*").replaceAll("\\*.*", "*");
  }

  @Override
  public String selectVersion(
      @Nullable String requestedRef,
      GitRepository repo,
      String url,
      Console console) throws RepoException, ValidationException {
    if (!Strings.isNullOrEmpty(requestedRef)) {
      if (requestedRef.startsWith("force:")) {
        return requestedRef.substring("force:".length());
      }
      console.warnFmt(
          "Ignoring '%s' as git.version_selector is being used. Run with "
              + "--nogit-origin-version-selector to override.",
          requestedRef);
    }
    Set<String> refs = repo.lsRemote(url, ImmutableList.of(asGitRefspec())).keySet();

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

    checkCondition(latestRef != null,
        "version_selector didn't match any version for '%s'", template.getBefore().pattern());

    // It is rare that a branch and a tag has the same name. The reason for this is that
    // destinations expect that the context_reference is a non-full reference. Also it is
    // more readable when we use it in transformations.
    if (latestRef.startsWith("refs/heads/")) {
      return latestRef.substring("refs/heads/".length());
    }
    if (latestRef.startsWith("refs/tags/")) {
      return latestRef.substring("refs/tags/".length());
    }
    return latestRef;
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

}
