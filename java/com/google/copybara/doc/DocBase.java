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
import com.google.common.collect.Iterables;
import com.google.copybara.doc.annotations.Example;
import java.util.List;
import java.util.TreeSet;
import javax.annotation.Nullable;

abstract class DocBase implements Comparable<DocBase> {

  protected final String name;
  protected final String description;

  DocBase(String name, String description) {
    this.name = checkNotNull(name);
    this.description = checkNotNull(description);
  }

  @Override
  public int compareTo(DocBase o) {
    return name.compareTo(o.name);
  }

  static final class DocModule extends DocBase {

    final TreeSet<DocField> fields = new TreeSet<>();
    final TreeSet<DocFunction> functions = new TreeSet<>();
    final TreeSet<DocFlag> flags = new TreeSet<>();

    DocModule(String name, String description) {
      super(name, description);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("name", name).toString();
    }

  }

  static final class DocFlag extends DocBase {

    final String type;

    DocFlag(String name, String type, String description) {
      super(name, description);
      this.type = type;
    }
  }

  static final class DocFunction extends DocBase {

    final TreeSet<DocFlag> flags = new TreeSet<>();
    @Nullable final String returnType;
    final ImmutableList<DocParam> params;
    final ImmutableList<DocExample> examples;

    DocFunction(
        String name,
        String description,
        @Nullable String returnType,
        Iterable<DocParam> params,
        Iterable<DocFlag> flags,
        Iterable<DocExample> examples) {
      super(name, description);
      this.returnType = returnType;
      this.params = ImmutableList.copyOf(params);
      this.examples = ImmutableList.copyOf(examples);
      Iterables.addAll(this.flags, flags);
    }
  }

  static final class DocParam extends DocBase {

    @Nullable final String defaultValue;
    final List<String> allowedTypes;

    DocParam(
        String name, @Nullable String defaultValue, List<String> allowedTypes, String description) {
      super(name, description);
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

  static final class DocField extends DocBase {

    @Nullable final String type;

    DocField(String name, String description, @Nullable String type) {
      super(name, description);
      this.type = type;
    }
  }
}