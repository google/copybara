package com.google.copybara.doc;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.auto.common.BasicAnnotationProcessor.ProcessingStep;
import com.google.common.base.Splitter;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.copybara.Origin;
import com.google.copybara.doc.annotations.DocElement;
import com.google.copybara.doc.annotations.DocField;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.util.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;

import javax.lang.model.element.Element;
import javax.tools.StandardLocation;

/**
 * WARNING: This test doesn't run with Bazel
 */
@RunWith(JUnit4.class)
public class MarkdownGeneratorTest {

  private JavacProcessingEnvironment env;
  private File tempDir;
  private ProcessingStep step;
  private HashMultimap<Class<? extends Annotation>, Element> byElement;

  @Before
  public void setup() throws IOException {
    tempDir = Files.createTempDir();
    MarkdownGenerator markdownGenerator = new MarkdownGenerator();
    Context context = new Context();
    // Magic happens here. It autoregisters in context.
    JavacFileManager jfm = new JavacFileManager(context, /*register=*/true, StandardCharsets.UTF_8);
    jfm.setLocation(StandardLocation.SOURCE_OUTPUT, ImmutableList.of(tempDir));
    env = JavacProcessingEnvironment.instance(context);
    markdownGenerator.init(env);
    step = markdownGenerator.initSteps().iterator().next();
    byElement = HashMultimap.create();
  }

  @Test
  public void testSimple() throws IOException {
    byElement.put(DocElement.class, classAsElement(Example.class));
    step.process(byElement);
    String content = Files.toString(new File(tempDir, "Origin.md"), StandardCharsets.UTF_8);
    assertThat(content).contains("yaml_name");
    assertThat(content).contains("my_description");

    Iterable<String> byLine = Splitter.on("\n").split(content);

    String fieldRequiredLine = findLine("fieldRequired", byLine);
    assertThat(fieldRequiredLine).contains("required;");
    assertThat(fieldRequiredLine).contains("field is required");

    String fieldOptionalLine = findLine("fieldOptional", byLine);
    assertThat(fieldOptionalLine).contains("optional;");
    assertThat(fieldOptionalLine).contains("field is optional");

    String fieldWithDefaultLine = findLine("fieldWithDefault", byLine);
    assertThat(fieldWithDefaultLine).contains("default_value;");
    assertThat(fieldWithDefaultLine).contains("field with default");

    String deprecatedField = findLine("deprecatedField", byLine);
    assertThat(deprecatedField).contains("DEPRECATED;");
    assertThat(deprecatedField).contains("this field has been deprecated");

    String someParamLine = findLine("someParam", byLine);
    assertThat(someParamLine).contains("does foo with bar");
  }

  private String findLine(String text, Iterable<String> lines) {
    for (String line : lines) {
      if (line.contains(text)) {
        return line;
      }
    }
    fail(text + " not found in " + lines);
    return null;
  }

  @Test
  public void testNoFlagsArgument() throws IOException {
    byElement.put(DocElement.class, classAsElement(NoFlagsDefined.class));
    step.process(byElement);
    String content = Files.toString(new File(tempDir, "Origin.md"), StandardCharsets.UTF_8);
    assertThat(content).contains("yaml_name");
    assertThat(content).contains("my_description");
  }

  @Test
  public void testEnums() throws IOException {
    byElement.put(DocElement.class, classAsElement(EnumExample.class));
    step.process(byElement);
    String content = Files.toString(new File(tempDir, "Origin.md"), StandardCharsets.UTF_8);
    assertThat(content).contains("one description");
    assertThat(content).contains("two description");
  }

  private ClassSymbol classAsElement(Class<?> clazz) {
    return env.getElementUtils().getTypeElement(clazz.getCanonicalName());
  }

  @DocElement(yamlName = "yaml_name", description = "my_description", elementKind = Origin.class)
  public static final class NoFlagsDefined {

  }

  @DocElement(yamlName = "yaml_name", description = "my_description", elementKind = Origin.class,
      flags = SomeOptions.class)
  public static final class Example {

    @DocField(description = "field is required")
    public void setFieldRequired(String name) {}

    @DocField(description = "field is optional", required = false)
    public void setFieldOptional(String name) {}

    @DocField(description = "field with default", defaultValue = "default_value")
    public void setFieldWithDefault(String name) {}

    @DocField(description = "this field has been deprecated", deprecated = true)
    public void setDeprecatedField(String name) {}
  }

  public enum MyTest {
    @DocField(description = "one description")
    ONE,
    @DocField(description = "two description")
    TWO
  }

  @DocElement(yamlName = "yaml_name", description = "my_description", elementKind = Origin.class,
      flags = SomeOptions.class)
  public static final class EnumExample {

    @DocField(description = "some field")
    public void setEnum(MyTest test) {
    }
  }

  @Parameters
  private static class SomeOptions {

    @Parameter(names = "someParam", description = "does foo with bar")
    private String someParam;
  }
}
