package com.google.copybara.config;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.Authoring;
import com.google.copybara.Change;
import com.google.copybara.ConfigValidationException;
import com.google.copybara.Destination;
import com.google.copybara.Origin;
import com.google.copybara.Origin.Reference;
import com.google.copybara.RepoException;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.testing.MapConfigFile;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.transform.Sequence;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.testing.TestingConsole;
import com.google.copybara.util.console.testing.TestingConsole.MessageType;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.skylarkinterface.SkylarkSignature;
import com.google.devtools.build.lib.syntax.BuiltinFunction;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.SkylarkList;
import com.google.devtools.build.lib.syntax.Type;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SkylarkParserTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  private SkylarkParser parser;
  private OptionsBuilder options;
  private TestingConsole console;

  @Before
  public void setup() {
    parser = new SkylarkParser(ImmutableSet.of(Mock.class, MockLabelsAwareModule.class));
    options = new OptionsBuilder();
    console = new TestingConsole();
    options.setConsole(console);
  }

  @Test
  public void requireAtLeastOneWorkflow()
      throws IOException, ConfigValidationException {
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("At least one workflow is required.");
    loadConfig("");
  }

  /**
   * This test checks that we can load a basic Copybara config file. This config file uses almost
   * all the features of the structure of the config file. Apart from that we include some testing
   * coverage on global values.
   */
  @Test
  public void testParseConfigFile()
      throws IOException, ConfigValidationException {
    String configContent = ""
        + "baz=42\n"
        + "some_url=\"https://so.me/random/url\"\n"
        + "\n"
        + "core.project(\n"
        + "  name = \"mytest\",\n"
        + ")\n"
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
        + "   authoring = authoring.overwrite('Copybara <no-reply@google.com>'),\n"
        + "   transformations = [\n"
        + "      mock.transform(field1 = \"foo\", field2 = \"bar\"),\n"
        + "      mock.transform(field1 = \"baz\", field2 = \"bee\"),\n"
        + "   ],\n"
        + "   exclude_in_origin = glob(['**/*.java']),\n"
        + "   exclude_in_destination = glob(['**/BUILD'], exclude = ['foo/BUILD']),\n"
        + ")\n";

    options.setWorkflowName("foo42");
    Config config = loadConfig(configContent);

    assertThat(config.getName()).isEqualTo("mytest");
    MockOrigin origin = (MockOrigin) config.getActiveWorkflow().origin();
    assertThat(origin.url).isEqualTo("https://so.me/random/url");
    assertThat(origin.branch).isEqualTo("master");

    MockDestination destination = (MockDestination) config.getActiveWorkflow().destination();
    assertThat(destination.folder).isEqualTo("some folder");

    Transformation transformation = config.getActiveWorkflow().transformation();
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

  private Config loadConfig(String configContent)
      throws IOException, ConfigValidationException {
    return parser.loadConfig(
        new MapConfigFile(ImmutableMap.of("copy.bara.sky", configContent.getBytes()),
            "copy.bara.sky"),
        options.build());
  }

  @Test
  public void testTransformsAreOptional()
      throws IOException, ConfigValidationException {
    String configContent = ""
        + "core.project(\n"
        + "  name = 'mytest',\n"
        + ")\n"
        + "\n"
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

    options.setWorkflowName("foo");
    Config config = loadConfig(configContent);

    assertThat(config.getName()).isEqualTo("mytest");
    Transformation transformation = config.getActiveWorkflow().transformation();
    assertThat(transformation.getClass()).isAssignableTo(Sequence.class);
    ImmutableList<? extends Transformation> transformations =
        ((Sequence) transformation).getSequence();
    assertThat(transformations).isEmpty();
  }

  @Test
  public void testGenericOfSimpleTypes()
      throws IOException, ConfigValidationException {
    String configContent = ""
        + "baz=42\n"
        + "some_url=\"https://so.me/random/url\"\n"
        + "\n"
        + "core.project(\n"
        + "  name = \"mytest\",\n"
        + ")\n"
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
      loadConfig(configContent);
      fail();
    } catch (ConfigValidationException e) {
      console.assertThat().onceInLog(MessageType.ERROR,
          "(\n|.)*expected value of type 'string' for element 1 of list, but got True \\(bool\\)"
              + "(\n|.)*");
    }
  }

  @Test
  public void testResolveLabel() throws Exception {
    String configContent = ""
        + "core.project(name = mock_labels_aware_module.read_foo())\n"
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

    Config config = parser.loadConfig(
        new MapConfigFile(
            ImmutableMap.of(
                "copy.bara.sky", configContent.getBytes(UTF_8),
                "foo", "stuff_in_foo".getBytes(UTF_8)),
            "copy.bara.sky"),
        options.build());
    assertThat(config.getName()).isEqualTo("stuff_in_foo");
  }

  /**
   * TODO(malcon): Migrate SkylarkParserTest.testNonReversibleTransform
   */
  public void disabledTestNonReversibleTransform() {

  }

  @SkylarkModule(
      name = "mock_labels_aware_module",
      doc = "LabelsAwareModule for testing purposes",
      category = SkylarkModuleCategory.BUILTIN,
      documented = false)
  public static class MockLabelsAwareModule implements LabelsAwareModule {
    private ConfigFile configFile;

    @Override
    public void setConfigFile(ConfigFile configFile) {
      this.configFile = configFile;
    }

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
          return new String(self.configFile.resolve("foo").content(), UTF_8);
        } catch (CannotResolveLabel|IOException inconceivable) {
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

    @SkylarkSignature(name = "transform", returnType = MockTransform.class,
        doc = "A mock Transform", objectType = Mock.class,
        parameters = {
            @Param(name = "self", type = Mock.class, doc = "self"),
            @Param(name = "field1", type = String.class, defaultValue = "None", noneable = true),
            @Param(name = "field2", type = String.class, defaultValue = "None", noneable = true),
            @Param(name = "list", type = SkylarkList.class,
                generic1 = String.class, defaultValue = "[]"),
        },
        documented = false)
    public static final BuiltinFunction transform = new BuiltinFunction("transform") {
      @SuppressWarnings("unused")
      public MockTransform invoke(Mock self, Object field1, Object field2, SkylarkList list)
          throws EvalException, InterruptedException {

        return new MockTransform(
            Type.STRING.convertOptional(field1, "field1"),
            Type.STRING.convertOptional(field2, "field2"),
            Type.STRING_LIST.convert(list, "list"));
      }
    };
  }

  public static class MockOrigin implements Origin<Reference> {

    private final String url;
    private final String branch;

    private MockOrigin(String url, String branch) {
      this.url = url;
      this.branch = branch;
    }

    @Override
    public void checkout(Reference ref, Path workdir) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Reference resolve(@Nullable String reference) throws RepoException {
      throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableList<Change<Reference>> changes(
        @Nullable Reference fromRef, Reference toRef, Authoring authoring) throws RepoException {
      throw new UnsupportedOperationException();
    }

    @Override
    public Change<Reference> change(Reference ref, Authoring authoring) throws RepoException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void visitChanges(Reference start, ChangesVisitor visitor, Authoring authoring)
        throws RepoException {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getLabelName() {
      return "Mock-RevId";
    }
  }

  public static class MockDestination implements Destination {

    private final String folder;

    private MockDestination(String folder) {
      this.folder = folder;
    }


    @Override
    public Writer newWriter() {
      throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public String getPreviousRef(String labelName) throws RepoException {
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

    public MockTransform(String field1, String field2, List<String> list) {
      this.field1 = field1;
      this.field2 = field2;
      this.list = list;
    }

    @Override
    public void transform(TransformWork work, Console console) throws IOException {
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
      return "MockTransform{" +
          "field1='" + field1 + '\'' +
          ", field2='" + field2 + '\'' +
          ", list=" + list +
          '}';
    }
  }
}
