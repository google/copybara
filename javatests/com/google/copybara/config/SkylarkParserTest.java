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

package com.google.copybara.config;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.Destination;
import com.google.copybara.Origin;
import com.google.copybara.Revision;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.Workflow;
import com.google.copybara.WriterContext;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.exception.CannotResolveLabel;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.transform.Sequence;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.skylarkinterface.SkylarkSignature;
import com.google.devtools.build.lib.syntax.BuiltinFunction;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.SkylarkList;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SkylarkParserTest {

  private static final String NON_IMPORTANT_WORKFLOW = "core.workflow(\n"
      + "   name = \"not_used\",\n"
      + "   origin = mock.origin(\n"
      + "      url = 'not_used',\n"
      + "      branch = \"not_used\",\n"
      + "   ),\n"
      + "   destination = mock.destination(\n"
      + "      folder = \"not_used\"\n"
      + "   ),\n"
      + "   authoring = authoring.overwrite('Copybara <not_used@google.com>'),\n"
      + ")\n";
  @Rule public final ExpectedException thrown = ExpectedException.none();

  private SkylarkTestExecutor parser;
  private TestingConsole console;

  @Before
  public void setup() {
    OptionsBuilder options = new OptionsBuilder();
    console = new TestingConsole();
    options.setConsole(console);
    parser = new SkylarkTestExecutor(options)
        .withStaticModules(ImmutableSet.of(Mock.class, MockLabelsAwareModule.class));
  }

  private String setUpInclusionTest() {
    parser.addConfigFile(
        "foo/authoring.bara.sky",
        ""
            + "load('bar', 'bar')\n"
            + "load('bar/foo', 'foobar')\n"
            + "baz=bar\n"
            + "def copy_author():\n"
            + "  return authoring.overwrite('Copybara <no-reply@google.com>')");
    parser.addConfigFile(
        "foo/bar.bara.sky",
        ""
            + "bar=42\n"
            + "load('bar/foo', 'foobar')\n"
            + "def copy_author():\n"
            + "  return authoring.overwrite('Copybara <no-reply@google.com>')");
    parser.addConfigFile("foo/bar/foo.bara.sky", "foobar=42\n");
    return ""
        + "load('//foo/authoring','copy_author', 'baz')\n"
        + "some_url=\"https://so.me/random/url\"\n"
        + "\n"
        + "core.workflow(\n"
        + "   name = \"foo\" + str(baz),\n"
        + "   origin = mock.origin(\n"
        + "      url = some_url,\n"
        + "      branch = \"master\",\n"
        + "   ),\n"
        + "   destination = mock.destination(\n"
        + "      folder = \"some folder\"\n"
        + "   ),\n"
        + "   authoring = copy_author(),\n"
        + "   transformations = [\n"
        + "      mock.transform(field1 = \"foo\", field2 = \"bar\"),\n"
        + "      mock.transform(field1 = \"baz\", field2 = \"bee\"),\n"
        + "   ],\n"
        + "   origin_files = glob(include = ['**'], exclude = ['**/*.java']),\n"
        + "   destination_files = glob(['foo/BUILD']),\n"
        + ")\n";
  }

  /**
   * This test checks that we can load a basic Copybara config file. This config file uses almost
   * all the features of the structure of the config file. Apart from that we include some testing
   * coverage on global values.
   */
  @Test
  public void testParseConfigFile() throws IOException, ValidationException {
    String configContent = setUpInclusionTest();
    Config config = parser.loadConfig(configContent);

    MockOrigin origin = (MockOrigin) getWorkflow(config, "foo42").getOrigin();
    assertThat(origin.url).isEqualTo("https://so.me/random/url");
    assertThat(origin.branch).isEqualTo("master");

    MockDestination destination = (MockDestination) getWorkflow(config, "foo42").getDestination();
    assertThat(destination.folder).isEqualTo("some folder");

    Transformation transformation = getWorkflow(config, "foo42").getTransformation();
    assertThat(transformation.getClass()).isAssignableTo(Sequence.class);
    ImmutableList<? extends Transformation> transformations =
        ((Sequence) transformation).getSequence();
    assertThat(transformations).hasSize(2);
    MockTransform transformation1 = (MockTransform) transformations.get(0);
    assertThat(transformation1.field1).isEqualTo("foo");
    assertThat(transformation1.field2).isEqualTo("bar");
    MockTransform transformation2 = (MockTransform) transformations.get(1);
    assertThat(transformation2.field1).isEqualTo("baz");
    assertThat(transformation2.field2).isEqualTo("bee");

  }

  /** This test checks that we can load the transitive includes of a config file. */
  @Test
  public void testLoadImportsOfConfigFile() throws Exception {
    String configContent = setUpInclusionTest();
    Map<String, ConfigFile> includeMap = parser.getConfigMap(configContent);
    assertThat(includeMap).containsKey("copy.bara.sky");
    assertThat(includeMap).containsKey("foo/authoring.bara.sky");
    assertThat(includeMap).containsKey("foo/bar.bara.sky");
    assertThat(includeMap).containsKey("foo/bar/foo.bara.sky");
  }

  /** Test that a dependency tree can be used as input for creating an equivalent tree */
  @Test
  public void testLoadImportsIdempotent() throws Exception {
    String configContent = setUpInclusionTest();
    Map<String, ConfigFile> includeMap = parser.getConfigMap(configContent);
    Map<String, byte[]> contentMap = new HashMap<>();
    Map<String, String> stringContentMap = new HashMap<>();
    for (Entry<String, ConfigFile> entry : includeMap.entrySet()) {
      contentMap.put(entry.getKey(), entry.getValue().readContentBytes());
      stringContentMap.put(entry.getKey(), entry.getValue().readContent());
    }
    ConfigFile derivedConfig =
        new MapConfigFile(ImmutableMap.copyOf(contentMap), "copy.bara.sky");
    Map<String, String> derivedContentMap = new HashMap<>();
    for (Entry<String, ConfigFile> entry : parser.getConfigMap(derivedConfig).entrySet()) {
      derivedContentMap.put(entry.getKey(), entry.getValue().readContent());
    }
    assertThat(derivedContentMap).isEqualTo(stringContentMap);
  }

  private Workflow<?, ?> getWorkflow(Config config, String name) throws ValidationException {
    return (Workflow<?, ?>) config.getMigration(name);
  }

  @Test
  public void testParseConfigCycleError() throws Exception {
    parseConfigCycleErrorTestHelper(() -> parser.loadConfig("load('//foo','foo')"));
  }

  @Test
  public void testLoadConfigFileAndTransitiveDepsCycle() throws Exception {
    parseConfigCycleErrorTestHelper(() -> parser.getConfigMap("load('//foo','foo')"));
  }

  private void parseConfigCycleErrorTestHelper(Callable<?> callable) throws Exception {
    try {
      parser.addConfigFile("foo.bara.sky", "load('//bar', 'bar')");
      parser.addConfigFile("bar.bara.sky", "load('//copy', 'copy')");
      callable.call();
      fail();
    } catch (ValidationException e) {
      assertThat(e.getMessage()).contains("Cycle was detected");
      console.assertThat().onceInLog(MessageType.ERROR,
          "(?m)Cycle was detected in the configuration: \n"
              + "\\* copy.bara.sky\n"
              + "  foo.bara.sky\n"
              + "  bar.bara.sky\n"
              + "\\* copy.bara.sky\n");
    }
  }

  @Test
  public void testTransformsAreOptional()
      throws IOException, ValidationException {
    String configContent = ""
        + "core.workflow(\n"
        + "   name = 'foo',\n"
        + "   origin = mock.origin(\n"
        + "      url = 'some_url',\n"
        + "      branch = 'master',\n"
        + "   ),\n"
        + "   destination = mock.destination(\n"
        + "      folder = 'some folder'\n"
        + "   ),\n"
        + "   authoring = authoring.overwrite('Copybara <no-reply@google.com>'),\n"
        + ")\n";

    Config config = parser.loadConfig(configContent);

    Transformation transformation = getWorkflow(config, "foo").getTransformation();
    assertThat(transformation.getClass()).isAssignableTo(Sequence.class);
    ImmutableList<? extends Transformation> transformations =
        ((Sequence) transformation).getSequence();
    assertThat(transformations).isEmpty();
  }

  @Test
  public void testGenericOfSimpleTypes()
      throws IOException, ValidationException {
    String configContent = ""
        + "baz=42\n"
        + "some_url=\"https://so.me/random/url\"\n"
        + "\n"
        + "core.workflow(\n"
        + "   name = \"default\",\n"
        + "   origin = mock.origin(\n"
        + "      url = some_url,\n"
        + "      branch = \"master\",\n"
        + "   ),\n"
        + "   destination = mock.destination(\n"
        + "      folder = \"some folder\"\n"
        + "   ),\n"
        + "   authoring = authoring.overwrite('Copybara <no-reply@google.com>'),\n"
        + "   transformations = [\n"
        + "      mock.transform(\n"
        + "         list = [\"some text\", True],"
        + "      ),\n"
        + "   ],\n"
        + ")\n";

    try {
      parser.loadConfig(configContent);
      fail();
    } catch (ValidationException e) {
      console
          .assertThat()
          .onceInLog(MessageType.ERROR, "(\n|.)*list: at index #1, got bool, want string(\n|.)*");
    }
  }

  private String prepareResolveLabelTest() {
    parser.addConfigFile("foo", "stuff_in_foo");
    parser.addConfigFile("bar", "stuff_in_bar");

    return ""
        + "mock_labels_aware_module.read_foo()\n"
        + "\n"
        + "core.workflow(\n"
        + "   name = \"default\",\n"
        + "   origin = mock.origin(\n"
        + "      url = 'some_url',\n"
        + "      branch = \"master\",\n"
        + "   ),\n"
        + "   destination = mock.destination(\n"
        + "      folder = \"some folder\"\n"
        + "   ),\n"
        + "   authoring = authoring.overwrite('Copybara <no-reply@google.com>'),\n"
        + ")\n";
  }

  @Test
  public void testResolveLabelDeps() throws Exception {
    String content = prepareResolveLabelTest();
    Map<String, ConfigFile> deps = parser.getConfigMap(content);
    assertThat(deps).hasSize(2);
    assertThat(deps.get("copy.bara.sky").readContent()).isEqualTo(content);
    assertThat(deps.get("foo").readContent()).isEqualTo("stuff_in_foo");
  }

  @Test
  public void testResolveLabel() throws Exception {
    parser.loadConfig(prepareResolveLabelTest());
  }

  /**
   * Test that the modules get the correct currentConfigFile when evaluating multi-file configs.
   */
  @Test
  public void testCurrentConfigFileWithLoad() throws Exception {
    parser.addConfigFile(
        "subfolder/foo.bara.sky", "subfolder_val = mock_labels_aware_module.read_foo()\n");
    parser.addConfigFile("subfolder/foo", "subfolder_foo");
    parser.addConfigFile("foo", "main_foo");

    String content = ""
        + "load('subfolder/foo', 'subfolder_val')\n"
        + "val = mock_labels_aware_module.read_foo()\n"
        + "\n"
        + NON_IMPORTANT_WORKFLOW;
    String val = parser.eval("val", content);
    String subfolderVal = parser.eval("subfolder_val", content);
    assertThat(subfolderVal).isEqualTo("subfolder_foo");
    assertThat(val).isEqualTo("main_foo");
  }

  @Test
  public void testParentEnvInmutable() throws Exception {
    parser.addConfigFile("foo.bara.sky", "my_list = [1, 2, 3]\n");

    String content = ""
        + "load('foo', 'my_list')\n"
        + "other = my_list\n"
        + "other += [4, 5]\n"
        + "\n"
        + NON_IMPORTANT_WORKFLOW
        + "";
    parser.evalProgramFails(content, ".*trying to mutate a frozen object.*");
  }

  @SkylarkModule(
      name = "mock_labels_aware_module",
      doc = "LabelsAwareModule for testing purposes",
      category = SkylarkModuleCategory.BUILTIN,
      documented = false)
  public static class MockLabelsAwareModule implements LabelsAwareModule {
    private ConfigFile configFile;

    @Override
    public void setConfigFile(ConfigFile mainConfigFile, ConfigFile currentConfigFile) {
      this.configFile = currentConfigFile;
    }

    @SuppressWarnings("unused")
    @SkylarkSignature(name = "read_foo", returnType = String.class,
        doc = "Read 'foo' label from config file",
        objectType = MockLabelsAwareModule.class,
        parameters = {
            @Param(name = "self", type = MockLabelsAwareModule.class, doc = "self"),
        },
        documented = false)
    public static final BuiltinFunction READ_FOO = new BuiltinFunction("read_foo") {
      public String invoke(MockLabelsAwareModule self) {
        try {
          return self.configFile.resolve("foo").readContent();
        } catch (CannotResolveLabel | IOException inconceivable) {
          throw new AssertionError(inconceivable);
        }
      }
    };
  }

  @SkylarkModule(
      name = "mock",
      doc = "Mock classes for testing SkylarkParser",
      category = SkylarkModuleCategory.BUILTIN,
      documented = false)
  public static class Mock {

    @SkylarkSignature(name = "origin", returnType = MockOrigin.class,
        doc = "A mock Origin", objectType = Mock.class,
        parameters = {
            @Param(name = "self", type = Mock.class, doc = "self"),
            @Param(name = "url", type = String.class, doc = "The origin url"),
            @Param(name = "branch", type = String.class, doc = "The origin branch",
                defaultValue = "\"master\""),
        },
        documented = false)
    public static final BuiltinFunction origin = new BuiltinFunction("origin") {
      @SuppressWarnings("unused")
      public MockOrigin invoke(Mock self, String url, String branch)
          throws EvalException, InterruptedException {
        return new MockOrigin(url, branch);
      }
    };

    @SkylarkSignature(name = "destination", returnType = MockDestination.class,
        doc = "A mock Destination", objectType = Mock.class,
        parameters = {
            @Param(name = "self", type = Mock.class, doc = "self"),
            @Param(name = "folder", type = String.class, doc = "The folder output"),
        },
        documented = false)
    public static final BuiltinFunction destination = new BuiltinFunction("destination") {
      @SuppressWarnings("unused")
      public MockDestination invoke(Mock self, String folder)
          throws EvalException, InterruptedException {
        return new MockDestination(folder);
      }
    };

    @SkylarkSignature(
        name = "transform",
        returnType = MockTransform.class,
        doc = "A mock Transform",
        objectType = Mock.class,
        parameters = {
          @Param(name = "self", type = Mock.class, doc = "self"),
          @Param(name = "field1", type = String.class, defaultValue = "None", noneable = true),
          @Param(name = "field2", type = String.class, defaultValue = "None", noneable = true),
          @Param(
              name = "list",
              type = SkylarkList.class,
              generic1 = String.class,
              defaultValue = "[]"),
        },
        documented = false)
    public static final BuiltinFunction transform =
        new BuiltinFunction("transform") {
          @SuppressWarnings("unused")
          public MockTransform invoke(Mock self, Object field1, Object field2, SkylarkList<?> list)
              throws EvalException, InterruptedException {
            return new MockTransform(
                SkylarkUtil.convertOptionalString(field1),
                SkylarkUtil.convertOptionalString(field2),
                SkylarkUtil.convertStringList(list, "list"));
          }
        };
  }

  public static class MockOrigin implements Origin<Revision> {

    private final String url;
    private final String branch;

    private MockOrigin(String url, String branch) {
      this.url = url;
      this.branch = branch;
    }

    @Override
    public Revision resolve(@Nullable String reference) throws RepoException {
      throw new UnsupportedOperationException();
    }

    @Override
    public Origin.Reader<Revision> newReader(Glob originFiles, Authoring authoring) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getLabelName() {
      return "Mock-RevId";
    }
  }

  public static class MockDestination implements Destination<Revision> {

    private final String folder;

    private MockDestination(String folder) {
      this.folder = folder;
    }

    @Override
    public Writer<Revision> newWriter(WriterContext writerContext) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getLabelNameWhenOrigin() {
      throw new UnsupportedOperationException();
    }
  }


  public static class MockTransform implements Transformation {

    private final String field1;
    private final String field2;
    private final List<String> list;

    @SuppressWarnings("WeakerAccess")
    public MockTransform(String field1, String field2, List<String> list) {
      this.field1 = field1;
      this.field2 = field2;
      this.list = list;
    }

    @Override
    public void transform(TransformWork work) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public Transformation reverse() {
      throw new UnsupportedOperationException("Non reversible");
    }

    @Override
    public String describe() {
      return "A mock translation";
    }

    @Override
    public String toString() {
      return "MockTransform{"
          + "field1='" + field1 + '\''
          + ", field2='" + field2 + '\''
          + ", list=" + list
          + '}';
    }
  }
}
