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

package com.google.copybara;

import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.copybara.doc.annotations.Example;
import com.google.copybara.doc.annotations.Examples;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests that the {@link Example}s from the documentation can be parsed correctly.
 */
@RunWith(JUnit4.class)
public class ExamplesTest {

  @Test
  public void testExamples() {

    SkylarkTestExecutor executor = getExecutor();

    ImmutableList.Builder<Result> resBuilder = ImmutableList.builder();
    for (Class<?> module : executor.getModules()) {
      for (Method method : module.getMethods()) {
        Examples examples = method.getAnnotation(Examples.class);
        ImmutableList<Example> samples;
        if (examples == null) {
          Example singleSample = method.getAnnotation(Example.class);
          if (singleSample != null) {
            samples = ImmutableList.of(singleSample);
          } else {
            continue;
          }
        } else {
          samples = ImmutableList.copyOf(examples.value());
        }
        resBuilder.addAll(checkExamples(executor, module, samples, method.getName()));
      }
    }
    ImmutableList<Result> result = resBuilder.build();

    // Normally we won't remove examples or modules. This checks that we don't go down. This
    // is the number of modules in Apr 2019. We can update this from time to time. It is not
    // critical to have an accurate number, but that we don't lose at least these.
    assertWithMessage("Less examples than expected").that(result.size()).isAtLeast(48);
    Set<? extends Class<?>> modules = result.stream().map(e -> e.cls).collect(Collectors.toSet());
    assertWithMessage("Less classes than expected: " + modules).that(modules.size()).isAtLeast(5);

    List<Result> errors = result.stream().filter(Result::isError).collect(Collectors.toList());

    assertWithMessage(
        "Errors in examples(" + errors.size() + "):\n\n"
            + Joiner.on("\n-----------------------------\n")
            .join(errors)).that(errors).isEmpty();
  }

  protected SkylarkTestExecutor getExecutor() {
    return new SkylarkTestExecutor(new OptionsBuilder());
  }

  private ImmutableList<Result> checkExamples(SkylarkTestExecutor executor, Class<?> module,
      ImmutableList<Example> samples, String name) {

    ImmutableList.Builder<Result> result = ImmutableList.builder();
    for (Example example : samples) {
      Object val;
      String exampleRef = module.getName() + "#" + name + ": " + example.title();
      try {
        val = Strings.isNullOrEmpty(example.testExistingVariable())
            ? executor.eval("a", "a=" + example.code())
            : executor.eval(example.testExistingVariable(),
                example.code());
        assertWithMessage(exampleRef).that(val).isNotNull();
        result.add(new Result(exampleRef, module, null));
      } catch (ValidationException | AssertionError e) {
        result.add(new Result(exampleRef, module, e));
      }
    }
    return result.build();
  }

  private static class Result {

    private final String name;
    private final Class<?> cls;
    @Nullable private final Throwable error;

    Result(String name, Class<?> cls, @Nullable Throwable error) {
      this.name = name;
      this.cls = cls;
      this.error = error;
    }

    private boolean isError() {
      return error != null;
    }

    @Override
    public String toString() {
      return name +":\n" + error;
    }
  }
}
