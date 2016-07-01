package com.google.copybara.config;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.jimfs.Jimfs;
import com.google.copybara.Authoring;
import com.google.copybara.Change;
import com.google.copybara.Destination;
import com.google.copybara.EnvironmentException;
import com.google.copybara.Options;
import com.google.copybara.Origin;
import com.google.copybara.RepoException;
import com.google.copybara.TransformResult;
import com.google.copybara.Workflow;
import com.google.copybara.doc.annotations.DocElement;
import com.google.copybara.doc.annotations.DocField;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.transform.Reverse;
import com.google.copybara.transform.Sequence;
import com.google.copybara.transform.Transformation;
import com.google.copybara.util.console.Console;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.constructor.ConstructorException;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.annotation.Nullable;

@RunWith(JUnit4.class)
public class YamlParserTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private FileSystem fs;
  private YamlParser yamlParser;
  private Options options;

  @Before
  public void setup() {
    yamlParser = new YamlParser(ImmutableList.of(
        new TypeDescription(MockOrigin.class, "!MockOrigin"),
        new TypeDescription(MockDestination.class, "!MockDestination"),
        new TypeDescription(MockTransform.class, "!MockTransform"),
        new TypeDescription(Reverse.Yaml.class, "!Reverse")
    ));
    fs = Jimfs.newFileSystem();
    options = new OptionsBuilder().build();
  }

  @Test
  public void testEmptyFile() throws IOException, ConfigValidationException, EnvironmentException {
    Files.write(fs.getPath("test"), "".getBytes());
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("'test' is empty");
    yamlParser.loadConfig(fs.getPath("test"), options);
  }

  @Test
  public void testEmptyFileWithSpaces()
      throws IOException, ConfigValidationException, EnvironmentException {
    Files.write(fs.getPath("test"), "  ".getBytes());
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("'test' is empty");
    yamlParser.loadConfig(fs.getPath("test"), options);
  }

  /**
   * This test checks that we can load a basic Copybara config file. This config file uses almost
   * all the features of the structure of the config file. Apart from that we include some testing
   * coverage on global values.
   */
  @Test
  public void testParseConfigFile()
      throws IOException, ConfigValidationException, EnvironmentException {
    String configContent = "name: \"mytest\"\n"
        + "global:\n"
        + "  - &some_url \"https://so.me/random/url\"\n"
        + "  - &transform_reference\n"
        + "    - !MockTransform\n"
        + "      field1: \"foo\"\n"
        + "      field2:  \"bar\"\n"
        + "    - !MockTransform\n"
        + "      field1: \"baz\"\n"
        + "      field2:  \"bee\"\n"
        + "workflows:\n"
        + "  - origin: !MockOrigin\n"
        + "      url: *some_url\n"
        + "      branch: \"master\"\n"
        + "    destination: !MockDestination\n"
        + "      folder: \"some folder\"\n"
        + "    transformations: *transform_reference\n";

    Files.write(fs.getPath("test"), configContent.getBytes());

    Config config = yamlParser.loadConfig(fs.getPath("test"), options);

    assertThat(config.getName()).isEqualTo("mytest");
    MockOrigin origin = (MockOrigin) config.getActiveWorkflow().getOrigin();
    assertThat(origin.url).isEqualTo("https://so.me/random/url");
    assertThat(origin.branch).isEqualTo("master");

    MockDestination destination = (MockDestination) config.getActiveWorkflow().getDestination();
    assertThat(destination.folder).isEqualTo("some folder");

    Transformation transformation = config.getActiveWorkflow().getTransformation();
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

  @Test
  public void testSingleTransform()
      throws IOException, ConfigValidationException, EnvironmentException {
    String configContent = "name: \"mytest\"\n"
        + "workflows:\n"
        + "  - origin: !MockOrigin\n"
        + "      url: some_url\n"
        + "      branch: \"master\"\n"
        + "    destination: !MockDestination\n"
        + "      folder: \"some folder\"\n"
        + "    transformations:\n"
        + "      - !MockTransform\n"
        + "        field1: \"foo\"\n"
        + "        field2:  \"bar\"\n";

    Files.write(fs.getPath("test"), configContent.getBytes());

    Config config = yamlParser.loadConfig(fs.getPath("test"), options);

    Transformation transformation = config.getActiveWorkflow().getTransformation();
    assertThat(transformation instanceof Sequence).isTrue();
    MockTransform mockTransform = (MockTransform) Iterables
        .getOnlyElement(((Sequence) transformation).getSequence());
    assertThat(mockTransform.field1).isEqualTo("foo");
    assertThat(mockTransform.field2).isEqualTo("bar");
  }

  @Test
  public void testNonReversibleTransform()
      throws IOException, ConfigValidationException, EnvironmentException {
    String configContent = "name: \"mytest\"\n"
        + "workflows:\n"
        + "  - origin: !MockOrigin\n"
        + "      url: some_url\n"
        + "      branch: \"master\"\n"
        + "    destination: !MockDestination\n"
        + "      folder: \"some folder\"\n"
        + "    transformations:\n"
        + "      - !Reverse \n"
        + "        original: !MockTransform\n"
        + "          field1: \"foo\"\n"
        + "          field2:  \"bar\"\n";

    Files.write(fs.getPath("test"), configContent.getBytes());

    thrown.expect(ConfigValidationException.class);
    thrown.expectCause(new CauseMatcher(NonReversibleValidationException.class,
        "'!MockTransform' transformation is not automatically reversible"));

    yamlParser.loadConfig(fs.getPath("test"), options);
  }

  @Test
  public void testGenericOfSimpleTypes()
      throws IOException, ConfigValidationException, EnvironmentException {
    String configContent = "name: \"mytest\"\n"
        + "workflows:\n"
        + "  - origin: !MockOrigin\n"
        + "      url: 'blabla'\n"
        + "      branch: \"master\"\n"
        + "    destination: !MockDestination\n"
        + "      folder: \"some folder\"\n"
        + "    transformations:\n"
        + "      - !MockTransform\n"
        + "        list:\n"
        + "          - \"some text\"\n"
        + "          - !!bool true\n";

    Files.write(fs.getPath("test"), configContent.getBytes());

    thrown.expect(ConfigValidationException.class);
    thrown.expectCause(new CauseMatcher(ConstructorException.class,
        "sequence field 'list' expects elements of type 'string',"
            + " but list[1] is of type 'boolean' (value = true)"));
    thrown.expectMessage("Error loading 'test' configuration file");

    yamlParser.loadConfig(fs.getPath("test"), options);
  }

  @Test
  public void testGenericOfWildcard()
      throws IOException, ConfigValidationException, EnvironmentException {
    String configContent = "name: \"mytest\"\n"
        + "workflows:\n"
        + "  - origin: !MockOrigin\n"
        + "      url: 'blabla'\n"
        + "      branch: \"master\"\n"
        + "    destination: !MockDestination\n"
        + "      folder: \"some folder\"\n"
        + "    transformations:\n"
        + "      - 42\n";

    Files.write(fs.getPath("test"), configContent.getBytes());

    thrown.expect(ConfigValidationException.class);
    thrown.expectCause(new CauseMatcher(ConstructorException.class,
        "sequence field 'transformations' expects elements of type"
            + " 'Transformation', but transformations[0] is of type 'integer' (value = 42)"));
    thrown.expectMessage("Error loading 'test' configuration file");

    yamlParser.loadConfig(fs.getPath("test"), options);
  }

  @Test
  public void requireAtLeastOneWorkflow() throws ConfigValidationException, EnvironmentException {
    Config.Yaml yaml = new Config.Yaml();
    yaml.setName("YamlParserTest");

    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("At least one element in 'workflows' is required.");
    yaml.setWorkflows(ImmutableList.<Workflow.Yaml>of());
    yaml.withOptions(options);
  }

  public static class MockOrigin implements Origin.Yaml<MockOrigin>, Origin<MockOrigin> {

    private String url;
    private String branch;

    public void setUrl(String url) {
      this.url = url;
    }

    public void setBranch(String branch) {
      this.branch = branch;
    }

    @Override
    public MockOrigin withOptions(Options options, Authoring authoring)
        throws ConfigValidationException {
      return this;
    }

    @Override
    public ReferenceFiles<MockOrigin> resolve(@Nullable String reference) throws RepoException {
      throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableList<Change<MockOrigin>> changes(@Nullable Reference<MockOrigin> fromRef,
        Reference<MockOrigin> toRef) throws RepoException {
      throw new UnsupportedOperationException();
    }

    @Override
    public Change<MockOrigin> change(Reference<MockOrigin> ref) throws RepoException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void visitChanges(Reference<MockOrigin> start, ChangesVisitor visitor)
        throws RepoException {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getLabelName() {
      return "Mock-RevId";
    }
  }

  public static class MockDestination implements Destination.Yaml, Destination {

    private String folder;

    public MockDestination() {
    }

    public void setFolder(String folder) {
      this.folder = folder;
    }

    @Override
    public Destination withOptions(Options options, String configName)
        throws ConfigValidationException {
      return this;
    }

    @Override
    public void process(TransformResult transformResult, Console console)
        throws RepoException, IOException {
      throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public String getPreviousRef(String labelName) throws RepoException {
      throw new UnsupportedOperationException();
    }
  }

  @DocElement(yamlName = "!MockTransform", description = "MockTransform",
      elementKind = Transformation.class)
  public static class MockTransform implements Transformation.Yaml, Transformation {

    private String field1;
    private String field2;
    private List<String> list;

    public MockTransform() {
    }

    @DocField(description = "field1")
    public void setField1(String field1) {
      this.field1 = field1;
    }

    @DocField(description = "field2")
    public void setField2(String field2) {
      this.field2 = field2;
    }

    @DocField(description = "list")
    public void setList(List<String> list) {
      for (Object s : list) {
        System.out.println(s + ":");
        System.out.println("  " + ((String) s));
      }
      this.list = list;
    }

    @Override
    public void transform(Path workdir, Console console) throws IOException {
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
    public Transformation withOptions(Options options) throws ConfigValidationException {
      return this;
    }

    @Override
    public void checkReversible() throws ConfigValidationException {
      throw new NonReversibleValidationException(this);
    }
  }

  /**
   * A matcher for exception causes types and messages
   */
  private static class CauseMatcher extends TypeSafeMatcher<Throwable> {

    private final Class<? extends Throwable> type;
    private final String expectedMessage;

    private CauseMatcher(Class<? extends Throwable> type, String expectedMessage) {
      this.type = type;
      this.expectedMessage = expectedMessage;
    }

    @Override
    protected boolean matchesSafely(Throwable item) {
      Throwable cause = item;
      while (cause != null) {
        if (cause.getClass().isAssignableFrom(type)
            && cause.getMessage().contains(expectedMessage)) {
          return true;
        }
        cause = cause.getCause();
      }
      return false;
    }

    @Override
    public void describeTo(Description description) {
      description.appendText("expects type ")
          .appendValue(type)
          .appendText(" and a message ")
          .appendValue(expectedMessage);
    }
  }
}
