// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.config.NonReversibleValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.transform.Transformation;
import com.google.copybara.transform.ValidationException;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.testing.TestingConsole;
import com.google.copybara.util.console.testing.TestingConsole.MessageType;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.skylarkinterface.SkylarkSignature;
import com.google.devtools.build.lib.syntax.BuiltinFunction;
import com.google.devtools.build.lib.syntax.EvalException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CoreReverseTest {

  private SkylarkTestExecutor skylark;
  private TestingConsole console;

  @Before
  public void setup() throws IOException {
    OptionsBuilder options = new OptionsBuilder();
    skylark = new SkylarkTestExecutor(options, Mock.class);
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
  public void reverseTypeError() throws Exception {
    try {
      skylark.<List<Transformation>>eval("foo", "foo = core.reverse([42])");
      fail();
    } catch (ConfigValidationException e) {
      console.assertThat().onceInLog(MessageType.ERROR,
          ".*expected type transformation for 'transformations' element but got type int"
              + " instead.*");
    }
  }

  @Test
  public void requiresReversibleTransform() throws Exception {
    try {
      skylark.eval("foo", "foo = core.reverse([\n"
          + "   mock.transform('foo', False),\n"
          + "   mock.transform('bar'),\n"
          + "])");
      fail();
    } catch (ConfigValidationException e) {
      console.assertThat().onceInLog(MessageType.ERROR, ".*foo is not reversible.*");
    }
  }

  @SkylarkModule(
      name = "mock",
      namespace = true,
      doc = "Mock classes for testing reverse",
      category = SkylarkModuleCategory.BUILTIN,
      documented = false)
  public static class Mock {

    @SkylarkSignature(name = "transform", returnType = Transformation.class,
        doc = "A mock Transform", objectType = Mock.class,
        parameters = {
            @Param(name = "field", type = String.class),
            @Param(name = "reversable", type = Boolean.class, defaultValue = "True"),
        },
        documented = false)
    public static final BuiltinFunction transform = new BuiltinFunction("transform") {
      @SuppressWarnings("unused")
      public Transformation invoke(String field, Boolean reversable)
          throws EvalException, InterruptedException {
        return new SimpleReplace(field,
            reversable ? new SimpleReplace("reverse " + field, null) : null);
      }
    };
  }

  private static class SimpleReplace implements Transformation {

    private final String text;
    private final SimpleReplace reverse;

    SimpleReplace(String text, SimpleReplace reverse) {
      this.text = text;
      this.reverse = reverse;
    }

    @Override
    public void transform(TransformWork work, Console console) {
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
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      SimpleReplace that = (SimpleReplace) o;
      return Objects.equals(text, that.text) &&
          Objects.equals(reverse, that.reverse);
    }

    @Override
    public int hashCode() {
      return Objects.hash(text, reverse);
    }
  }
}
