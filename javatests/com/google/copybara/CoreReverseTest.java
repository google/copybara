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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableSet;
import com.google.copybara.exception.NonReversibleValidationException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.transform.ExplicitReversal;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.util.List;
import java.util.Objects;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.StarlarkValue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CoreReverseTest {

  private SkylarkTestExecutor skylark;
  private TestingConsole console;

  @Before
  public void setup() {
    OptionsBuilder options = new OptionsBuilder();
    skylark = new SkylarkTestExecutor(options)
    .withStaticModules(ImmutableSet.of(Mock.class));
    console = new TestingConsole();
    options.setConsole(console);
  }

  @Test
  public void reverseTest() throws Exception {
    List<Transformation> reverse = skylark.eval("foo", ""
        + "foo = core.reverse([\n"
        + "   mock.transform('foo'),\n"
        + "   mock.transform('bar'),\n"
        + "])");
    assertThat(reverse).containsExactly(
        new SimpleReplace("reverse bar", null),
        new SimpleReplace("reverse foo", null));
  }

  @Test
  public void reverseTest2() throws Exception {
    List<Transformation> reverse =
        skylark.eval(
            "foo",
            ""
                + "def test(ctx):\n"
                + "    ctx.console.info('Foo')\n"
                + "\n"
                + "foo = core.reverse([\n"
                + "    mock.transform('foo'),\n"
                + "    core.transform([test]),\n"
                + "])");
    assertThat(reverse.get(0)).isInstanceOf(ExplicitReversal.class);
    assertThat(reverse.get(1)).isEqualTo(new SimpleReplace("reverse foo", null));
  }

  @Test
  public void reverseSingle() throws Exception {
    assertThat(skylark.<List<Transformation>>eval(
        "foo", "foo = core.reverse([mock.transform('foo')])"))
        .containsExactly(new SimpleReplace("reverse foo", null));
  }

  @Test
  public void reverseEmpty() throws Exception {
    assertThat(skylark.<List<Transformation>>eval("foo", "foo = core.reverse([])")).isEmpty();
  }

  @Test
  public void reverseTypeError() {
    assertThrows(
        ValidationException.class,
        () -> skylark.<List<Transformation>>eval("foo", "foo = core.reverse([42])"));
    console
        .assertThat()
        .onceInLog(
            MessageType.ERROR,
            "Error in reverse: for 'transformations' element, got int, want function or"
                + " transformation");
  }

  @Test
  public void requiresReversibleTransform() {
    assertThrows(
        ValidationException.class,
        () ->
            skylark.eval(
                "foo",
                "foo = core.reverse([\n"
                    + "   mock.transform('foo', False),\n"
                    + "   mock.transform('bar'),\n"
                    + "])"));
    console.assertThat().onceInLog(MessageType.ERROR, ".*foo is not reversible.*");
  }

  @StarlarkBuiltin(
      name = "mock",
      doc = "Mock classes for testing reverse",
      documented = false)
  public static class Mock implements StarlarkValue {

    @StarlarkMethod(
        name = "transform",
        doc = "A mock Transform",
        parameters = {
          @Param(name = "field"),
          @Param(name = "reversable", defaultValue = "True"),
        },
        documented = false)
    public Transformation transform(String field, Boolean reversable) {
      return new SimpleReplace(
          field, reversable ? new SimpleReplace("reverse " + field, null) : null);
    }
  }

  private static class SimpleReplace implements Transformation {

    private final String text;
    private final SimpleReplace reverse;

    SimpleReplace(String text, SimpleReplace reverse) {
      this.text = text;
      this.reverse = reverse;
    }

    @Override
    public TransformationStatus transform(TransformWork work) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Transformation reverse() throws NonReversibleValidationException {
      if (reverse != null) {
        return reverse;
      }
      throw new NonReversibleValidationException(text + " is not reversible");
    }

    @Override
    public String describe() {
      return text;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof SimpleReplace)) {
        return false;
      }
      SimpleReplace that = (SimpleReplace) o;
      return Objects.equals(text, that.text)
          && Objects.equals(reverse, that.reverse);
    }

    @Override
    public int hashCode() {
      return Objects.hash(text, reverse);
    }
  }
}
