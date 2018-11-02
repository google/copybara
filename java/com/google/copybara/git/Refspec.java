/*
 * Copyright (C) 2016 Google Inc.
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

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.copybara.exception.ValidationException;
import java.nio.file.Path;
import java.util.List;

/**
 * Represents a git refspec.
 */
public class Refspec {

  private final String origin;
  private final String destination;
  private final boolean allowNoFastForward;

  private Refspec(String origin, String destination, boolean allowNoFastForward) {
    this.origin = origin;
    this.destination = destination;
    this.allowNoFastForward = allowNoFastForward;
  }

  public String getOrigin() {
    return origin;
  }

  public String getDestination() {
    return destination;
  }

  boolean isAllowNoFastForward() {
    return allowNoFastForward;
  }

  /**
   * Converts a reference from the origin to the destination reference using the refspec.
   *
   * <p>Note that the {@code originRef} should match the origin refspec.
   */
  public String convert(String originRef) {
    if (!origin.contains("*")) {
      Preconditions.checkArgument(originRef.equals(origin),
          "originRef=%s origin=%s", originRef, origin);
      return destination;
    } else {
      List<String> origSplit = Splitter.on('*').splitToList(origin);
      Preconditions.checkState(origSplit.size() == 2);
      String fromPrefix = origSplit.get(0);
      String fromSuffix = origSplit.get(1);
      Preconditions.checkArgument(
          originRef.startsWith(fromPrefix) && originRef.endsWith(fromSuffix),
          "originRef=%s origin=%s", originRef, origin);
      String middle = originRef.substring(fromPrefix.length(),
          originRef.length() - fromSuffix.length());

      List<String> destSplit = Splitter.on('*').splitToList(destination);
      Preconditions.checkState(destSplit.size() == 2);
      String toPrefix = destSplit.get(0);
      String toSuffix = destSplit.get(1);
      return toPrefix + middle + toSuffix;
    }
  }

  /**
   * Tests whether a ref matches the origin pattern of the refspec.
   *
   * <p>Note that the {@code originRef} should match the origin refspec.
   */
  public boolean matchesOrigin(String originRef) {
    if (!origin.contains("*")) {
      return originRef.equals(origin);
    } else {
      List<String> origSplit = Splitter.on('*').splitToList(origin);
      Preconditions.checkState(origSplit.size() == 2);
      String fromPrefix = origSplit.get(0);
      String fromSuffix = origSplit.get(1);
      return originRef.startsWith(fromPrefix) && originRef.endsWith(fromSuffix);
    }
  }

  public Refspec withAllowNoFastForward() {
    return new Refspec(origin, destination, /*allowNoFastForward*/true);
  }

  public Refspec originToOrigin() {
    return new Refspec(origin, origin, allowNoFastForward);
  }

  public Refspec destinationToDestination() {
    return new Refspec(destination, destination, allowNoFastForward);
  }

  public Refspec invert() {
    return new Refspec(destination, origin, allowNoFastForward);
  }

  /** Same as {@see #create}, but does not provide Location data. */
  public static Refspec createBuiltin(GitEnvironment gitEnv, Path cwd, String refspecParam)
      throws ValidationException {
    return create(gitEnv, cwd, refspecParam);
  }

  public static Refspec create(GitEnvironment gitEnv, Path cwd, String refspecParam)
      throws InvalidRefspecException {
    if (refspecParam.isEmpty()) {
      throw new InvalidRefspecException("Empty refspec is not allowed");
    }
    boolean allowNoFastForward = false;
    String refspecStr = refspecParam;
    if (refspecStr.startsWith("+")) {
      allowNoFastForward = true;
      refspecStr = refspecStr.substring(1);
    }
    List<String> elements = Splitter.on(':').splitToList(refspecStr);
    if (elements.size() > 2) {
      throw new InvalidRefspecException("Invalid refspec. Multiple ':' found: '" + refspecParam);
    }
    String origin = elements.get(0);
    String destination = origin;
    GitRepository.validateRefSpec(gitEnv, cwd, origin);
    if (elements.size() > 1) {
      destination = elements.get(1);
      GitRepository.validateRefSpec(gitEnv, cwd, destination);
    }
    if (origin.contains("*") != destination.contains("*")) {
      throw new InvalidRefspecException(
          "Wildcard only used in one part of the refspec: " + refspecParam);
    }
    return new Refspec(origin, destination, allowNoFastForward);
  }

  @Override
  public String toString() {
     return (allowNoFastForward ? "+" : "") + origin + ":" + destination;
  }

}
