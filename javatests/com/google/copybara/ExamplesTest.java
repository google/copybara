package com.google.copybara;

import static com.google.common.truth.Truth.assertWithMessage;

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
        if (examples == null) {
          continue;
        }
        for (Example example : examples.value()) {
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
    return Copybara.BASIC_MODULES;
  }

}
