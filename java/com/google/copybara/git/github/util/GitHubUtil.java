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

package com.google.copybara.git.github.util;

import static com.google.copybara.exception.ValidationException.checkCondition;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.exception.ValidationException;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.net.URI;
import java.util.Optional;

/**
 * General utilities for manipulating GitHub urls and data
 */
public class GitHubUtil {

  private static final Pattern GITHUB_PULL_REQUEST =
      Pattern.compile("https://github[.]com/(.+)/pull/([0-9]+)");
  private static final String GIT_GITHUB_PROTOCOL = "git@github.com:";
  private static final ImmutableSet<String> GITHUB_HOSTS =
      ImmutableSet.of("www.github.com", "github.com");

  private GitHubUtil() {
  }

  /**
   * Return the username part of a github url. For example in
   * https://github.com/foo/bar/baz, 'foo' would be the user.
   */
  public static String getUserNameFromUrl(String url) throws ValidationException {
    String project = getProjectNameFromUrl(url);
    int i = project.indexOf("/");
    return i == -1 ? project : project.substring(0, i);
  }

  /**
   * Return the repository part of a github url. For example in https://github.com/foo/bar/baz,
   * 'bar' would be the repository.
   */
  public static String getRepoNameFromUrl(String url) throws ValidationException {
    String project = getProjectNameFromUrl(url);
    int start = project.indexOf("/");
    if (start < 0) {
      throw new ValidationException("Cannot find repo name from url " + url);
    }
    int end = project.indexOf("/", start + 1);
    return end < 0 ? project.substring(start + 1) : project.substring(start + 1, end);
  }

  /**
   * Given a url that represents a GitHub repository, return the project name.
   */
  public static String getProjectNameFromUrl(String url) throws ValidationException {
    checkCondition(!Strings.isNullOrEmpty(url), "Empty url");

    if (url.startsWith(GIT_GITHUB_PROTOCOL)) {
      return url.substring(GIT_GITHUB_PROTOCOL.length()).replaceAll("([.]git|/)$", "");
    }
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException e) {
      throw new ValidationException("Cannot find project name from url " + url);
    }
    if (uri.getScheme() == null) {
      uri = URI.create("notimportant://" + url);
    }
    checkCondition(GITHUB_HOSTS.contains(uri.getHost()), "Not a github url: %s", url);
    String name = uri.getPath()
        .replaceAll("^/", "")
        .replaceAll("([.]git|/)$", "");

    checkCondition(!Strings.isNullOrEmpty(name), "Cannot find project name from url %s", url);
    return name;
  }

  /**
   * Returns true if url is a GitHub url.
   */
  public static boolean isGitHubUrl(String url) {
    try {
      GitHubUtil.getProjectNameFromUrl(url);
      return true;
    } catch (ValidationException e) {
      return false;
    }
  }

  /**
   * Returns a valid branch name by replacing invalid character with "_"
   * throw ValidationException when branchName starts with "/" or "refs/"
   */
  public static String getValidBranchName(String branchName)
      throws ValidationException {
    checkCondition(!branchName.startsWith("/") && !branchName.startsWith("refs/"),
        "Branch name has invalid prefix: \"/\" or \"refs/\"");
    return branchName.replaceAll("[^A-Za-z0-9/_-]", "_");
  }

  /**
   * Given a project name, like copybara/google, return the GitHub https url of it.
   */
  public static String asGithubUrl(String project) {
    return "https://github.com/" + project;
  }

  private static final Pattern GITHUB_PULL_REQUEST_REF =
      Pattern.compile("refs/pull/([0-9]+)/(head|merge)");

  /**
   * Given a ref like 'refs/pull/12345/head' returns 12345 or null it not a GitHub PR ref
   */
  public static Optional<Integer> maybeParseGithubPrFromHeadRef(String ref) {
    Matcher matcher = GITHUB_PULL_REQUEST_REF.matcher(ref);
    return (matcher.matches() && "head".equals(matcher.group(2)))
           ? Optional.of(Integer.parseInt(matcher.group(1)))
           : Optional.empty();
  }

  /**
   * Given a ref like 'refs/pull/12345/merge' returns 12345 or null it not a GitHub PR ref
   */
  public static Optional<Integer> maybeParseGithubPrFromMergeOrHeadRef(String ref) {
    Matcher matcher = GITHUB_PULL_REQUEST_REF.matcher(ref);
    return matcher.matches() ? Optional.of(Integer.parseInt(matcher.group(1))) : Optional.empty();
  }

  public static Optional<GitHubPrUrl> maybeParseGithubPrUrl(String ref) {
    Matcher matcher = GITHUB_PULL_REQUEST.matcher(ref);
    return matcher.matches()
           ? Optional.of(new GitHubPrUrl(matcher.group(1), Integer.parseInt(matcher.group(2))))
           : Optional.empty();
  }

  /**
   * Given a prNumber return a git reference like 'refs/pull/12345/head'
   */
  public static String asHeadRef(int prNumber) {
    return "refs/pull/" + prNumber + "/head";
  }

  /**
   * Given a prNumber return a git reference like 'refs/pull/12345/merge'
   */
  public static String asMergeRef(int prNumber) {
    return "refs/pull/" + prNumber + "/merge";
  }

  /**
   * A GitHub PR project and number
   */
  public static class GitHubPrUrl {

    private final String project;
    private final int prNumber;

    GitHubPrUrl(String project, int prNumber) {
      this.project = project;
      this.prNumber = prNumber;
    }

    public String getProject() {
      return project;
    }

    public int getPrNumber() {
      return prNumber;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("project", project)
          .add("prNumber", prNumber)
          .toString();
    }
  }
}
