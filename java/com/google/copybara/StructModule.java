package com.google.copybara;
/*
 * Copyright (C) 2022 Google Inc.
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
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.doc.annotations.Example;
import javax.annotation.Nullable;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Printer;
import net.starlark.java.eval.StarlarkValue;
import net.starlark.java.eval.Structure;

/** struct() constructor */
@StarlarkBuiltin(name = "struct", doc = "Immutable struct type.")
public class StructModule implements StarlarkValue {

  @StarlarkMethod(
      name = "constructor",
      doc =
          "Creates a new immutable struct. Structs with the same keys/values are equal. The "
              + "struct's keys and values are passed in as keyword arguments.",
      selfCall = true,
      extraKeywords =
          @Param(
              name = "kwargs",
              defaultValue = "{}",
              doc = "Dictionary of Args."))
  @Example(
      before = "Structs are immutable objects to group values.",
      title = "Create a struct",
      code = "my_struct = struct(foo='bar')\nx = my_struct.foo",
      testExistingVariable = "x")
  public StructImpl create(Dict<String, Object> kwargs) {
    return new StructImpl(ImmutableMap.copyOf(kwargs));
  }

  /**
   * Trivial struct implementation based on ImmutableMap.
   */
  static class StructImpl implements Structure {

    private final ImmutableMap<String, Object> dict;

    public StructImpl(ImmutableMap<String, Object> dict) {
      this.dict = checkNotNull(dict);
    }

    @Nullable
    @Override
    public Object getValue(String name) throws EvalException {
      if (!dict.containsKey(name)) {
        throw new EvalException(getErrorMessageForUnknownField(name));
      }
      return dict.get(name);
    }

    @Override
    public ImmutableCollection<String> getFieldNames() {
      return dict.keySet();
    }

    @Nullable
    @Override
    public String getErrorMessageForUnknownField(String field) {
      return String.format("Field %s is unknown, available fields are %s.", field, getFieldNames());
    }

    @Override
    public void repr(Printer printer) {
      printer.append(
          String.format(
              "struct(%s)",
              dict.entrySet().stream()
                  .map(e -> String.format("%s=%s", e.getKey(), new Printer().repr(e.getValue())))
                  .collect(joining(", "))));
    }

    @Override
    public boolean isImmutable() {
      return true;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof StructImpl)) {
        return false;
      }
      return this.dict.equals(((StructImpl) other).dict);
    }

    @Override
    public int hashCode() {
      return dict.hashCode();
    }
  }

}
