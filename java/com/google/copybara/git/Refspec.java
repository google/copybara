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
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.syntax.EvalException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

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
  String convert(String originRef) {
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

  public static Refspec create(Map<String, String> env, Path cwd, String refspecParam,
      Location location) throws EvalException {
    if (refspecParam.isEmpty()) {
      throw new EvalException(location, "Empty refspec is not allowed");
    }
    boolean allowNoFastForward = false;
    String refspecStr = refspecParam;
    if (refspecStr.startsWith("+")) {
      allowNoFastForward = true;
      refspecStr = refspecStr.substring(1);
    }
    List<String> elements = Splitter.on(':').splitToList(refspecStr);
    if (elements.size() > 2) {
      throw new EvalException(location, "Invalid refspec. Multiple ':' found: '" + refspecParam);
    }
    String origin = elements.get(0);
    String destination = origin;
    GitRepository.validateRefSpec(location, env, cwd, origin);
    if (elements.size() > 1) {
      destination = elements.get(1);
      GitRepository.validateRefSpec(location, env, cwd, destination);
    }
    if (origin.contains("*") != destination.contains("*")) {
      throw new EvalException(location,
          "Wilcard only used in one part of the refspec: " + refspecParam);
    }
    return new Refspec(origin, destination, allowNoFastForward);
  }

  public String toString() {
     return (allowNoFastForward ? "+" : "") + origin + ":" + destination;
  }

}
