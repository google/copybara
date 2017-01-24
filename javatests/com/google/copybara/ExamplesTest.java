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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.copybara.doc.annotations.Example;
import com.google.copybara.doc.annotations.Examples;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import java.lang.reflect.Field;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests that the {@link Example}s from the documentation can be parsed correctly.
 */
@RunWith(JUnit4.class)
public class ExamplesTest {

  @Test
  public void testExamples() throws ValidationException {

    SkylarkTestExecutor executor = new SkylarkTestExecutor(new OptionsBuilder(),
        Iterables.toArray(getUserModules(), Class.class));

    boolean anyFound = false;
    for (Class<?> module : executor.getModules()) {
      for (Field field : module.getDeclaredFields()) {
        Examples examples = field.getAnnotation(Examples.class);
        ImmutableList<Example> samples;
        if (examples == null) {
          Example singleSample = field.getAnnotation(Example.class);
          if (singleSample != null) {
            samples = ImmutableList.of(singleSample);
          } else {
            continue;
          }
        } else {
          samples = ImmutableList.copyOf(examples.value());
        }
        for (Example example : samples) {
          anyFound = true;
          Object val = executor.eval("a", "a=" + example.code());
          assertWithMessage(module.getName() + "#" + field.getName() + ": " + example.title())
              .that(val).isNotNull();
        }
      }
    }
    assertWithMessage("Could not find any example to run!").that(anyFound).isTrue();
  }

  protected ImmutableSet<Class<?>> getUserModules() {
    return new ModuleSupplier().getModules();
  }

}
