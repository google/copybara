/*
 * Copyright (C) 2021 Google Inc.
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

package com.google.copybara.doc;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.copybara.doc.annotations.Example;
import java.util.List;
import java.util.TreeSet;
import javax.annotation.Nullable;

/** Helper for generating documentation from the Starlark annotations in the Copybara codebase. */
public abstract class DocBase implements Comparable<DocBase> {

  protected final String name;
  protected final String description;
  private final boolean isDocumented;

  DocBase(String name, String description, boolean isDocumented) {
    this.name = checkNotNull(name);
    this.description = checkNotNull(description);
    this.isDocumented = isDocumented;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public boolean isDocumented() {
    return isDocumented;
  }

  @Override
  public int compareTo(DocBase o) {
    return name.compareTo(o.name);
  }

  /** Module level */
  public static final class DocModule extends DocBase {

    final TreeSet<DocField> fields = new TreeSet<>();
    final TreeSet<DocFunction> functions = new TreeSet<>();
    final TreeSet<DocFlag> flags = new TreeSet<>();

    DocModule(String name, String description, boolean isDocumented) {
      super(name, description, isDocumented);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("name", name).toString();
    }

    public ImmutableSet<DocFunction> getFunctions() {
      return ImmutableSet.copyOf(functions);
    }

    public ImmutableSet<DocField> getFields() {
      return ImmutableSet.copyOf(fields);
    }
  }

  static final class DocFlag extends DocBase {

    final String type;

    DocFlag(String name, String type, String description, boolean isDocumented) {
      super(name, description, isDocumented);
      this.type = type;
    }
  }

  /** Function level */
  public static final class DocFunction extends DocBase {

    final TreeSet<DocFlag> flags = new TreeSet<>();
    @Nullable final String returnType;
    final ImmutableList<DocParam> params;
    final ImmutableList<DocExample> examples;

    public final boolean hasStar;
    public final boolean hasStarStar;
    public final boolean isSelfCall;

    public ImmutableList<DocParam> getParams() {
      return params;
    }

    public String getReturnType() {
      return DocBase.handleType(returnType);
    }

    DocFunction(
        String name,
        String description,
        @Nullable String returnType,
        Iterable<DocParam> params,
        Iterable<DocFlag> flags,
        Iterable<DocExample> examples,
        boolean hasStar,
        boolean hasStarStar,
        boolean isSelfCall,
        boolean isDocumented) {
      super(name, description, isDocumented);
      this.returnType = returnType;
      this.params = ImmutableList.copyOf(params);
      this.examples = ImmutableList.copyOf(examples);
      this.hasStar = hasStar;
      this.hasStarStar = hasStarStar;
      this.isSelfCall = isSelfCall;
      Iterables.addAll(this.flags, flags);
    }
  }

  /** Function parameter level */
  public static final class DocParam extends DocBase {

    @Nullable final String defaultValue;
    final List<String> allowedTypes;

    @Nullable
    public String getDefaultValue() {
      return defaultValue;
    }

    public ImmutableList<String> getAllowedTypes() {
      return ImmutableList.copyOf(allowedTypes);
    }

    DocParam(
        String name,
        @Nullable String defaultValue,
        List<String> allowedTypes,
        String description,
        boolean isDocumented) {
      super(name, description, isDocumented);
      this.defaultValue = defaultValue;
      this.allowedTypes = allowedTypes;
    }
  }

  static final class DocExample {

    final Example example;

    DocExample(Example example) {
      this.example = example;
    }
  }

  /** Field level */
  public static final class DocField extends DocBase {

    @Nullable final String type;

    DocField(String name, String description, @Nullable String type, boolean isDocumented) {
      super(name, description, isDocumented);
      this.type = type;
    }

    public String getType() {
      return DocBase.handleType(type);
    }
  }

  private static String handleType(String type) {
    return type == null ? "NoneType" : type;
  }
}