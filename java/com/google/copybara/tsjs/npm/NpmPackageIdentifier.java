/*
 * Copyright (C) 2024 Google LLC.
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
package com.google.copybara.tsjs.npm;

import com.google.common.base.MoreObjects;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.copybara.exception.ValidationException;

/**
 * An NpmPackageIdentifier is a structured representation of an NPM package's name and scope values.
 */
final class NpmPackageIdentifier {

  public final String scope;
  public final String name;

  private NpmPackageIdentifier(String scope, String name) {
    this.scope = scope;
    this.name = name;
  }

  static NpmPackageIdentifier fromPackage(String packageName) throws ValidationException {
    ImmutableList<String> parts = ImmutableList.copyOf(Splitter.on('/').split(packageName));
    if (parts.size() == 1) {
      return new NpmPackageIdentifier("", packageName);
    }
    ValidationException.checkCondition(
        parts.size() == 2, "probably invalid package name %s", packageName);
    String scope = parts.get(0);
    String name = parts.get(1);
    ValidationException.checkCondition(
        scope.charAt(0) == '@', "package scopes should start with \"@\"");
    return new NpmPackageIdentifier(scope.substring(1), name);
  }

  public String toHumanReadableName() {
    if (scope.isEmpty()) {
      return name;
    }
    return String.format("@%s/%s", scope, name);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("scope", scope).add("package", name).toString();
  }
}
