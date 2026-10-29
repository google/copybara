/*
 * Copyright (C) 2016 Google LLC
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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.config.Config;
import com.google.copybara.config.ConfigFile;
import com.google.copybara.exception.CannotResolveLabel;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.ExitCode;
import com.google.copybara.util.console.AnsiColor;
import com.google.copybara.util.console.AnsiConsole;
import com.google.copybara.util.console.Console;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test to verify that framework methods are called as expected.
 */
@RunWith(JUnit4.class)
public class MainTest {

  private SkylarkTestExecutor skylark;
  private final String[] args = {"copy.bara.sky"};
  private boolean called = false;

  private OptionsBuilder options;

  @Before
  public void setUp() throws Exception {
    called = false;
    Path userHomeDir = Files.createTempDirectory("MainTest");
    System.setProperty("user.home", userHomeDir.toString());
    options = new OptionsBuilder();
    skylark = new SkylarkTestExecutor(options);
  }

  @Test
  public void testNoArguments() throws IOException {
    ImmutableMap<String, String> envWithHome = ImmutableMap.of("HOME",
        Files.createTempDirectory("foo").toString());
    assertThat(new Main(envWithHome).run(/*no arguments*/ new String[]{}))
        .isEqualTo(ExitCode.COMMAND_LINE_ERROR);
  }

  @Test
  public void testConfigureLogCalled() throws IOException {

    ImmutableMap<String, String> envWithHome = ImmutableMap.of("HOME",
        Files.createTempDirectory("foo").toString());
    Main main = new Main(envWithHome) {
          @Override
          protected void configureLog(FileSystem fs, String[] args) {
            called = true;
          }
        };
    assertThat(main.run(args)).isEqualTo(ExitCode.COMMAND_LINE_ERROR);
    assertThat(called).isTrue();
  }

  @Test
  public void testTemporaryFeatures() throws IOException {

    ImmutableMap<String, String> envWithHome = ImmutableMap.of("HOME",
        Files.createTempDirectory("foo").toString());

    Main main = new Main(envWithHome) {
      @Override
      protected ModuleSet newModuleSet(ImmutableMap<String, String> environment, FileSystem fs,
          Console console) {
        return skylark.createModuleSet();
      }
    };

    main.run(new String[]{"--temporary-features", "foo:true,bar:false"});
    assertThat(options.general.isTemporaryFeature("foo", true)).isTrue();
    assertThat(options.general.isTemporaryFeature("foo", false)).isTrue();
    assertThat(options.general.isTemporaryFeature("bar", true)).isFalse();
    assertThat(options.general.isTemporaryFeature("bar", false)).isFalse();
    assertThat(options.general.isTemporaryFeature("baz", true)).isTrue();
    assertThat(options.general.isTemporaryFeature("baz", false)).isFalse();
  }

  @Test
  public void testRelativePath() throws IOException, ValidationException {

    ImmutableMap<String, String> envWithHome = ImmutableMap.of("HOME",
        Files.createTempDirectory("foo").toString());
    Main main = new Main(envWithHome) {
          @Override
          protected void configureLog(FileSystem fs, String[] args) {
            called = true;
          }

      @Override
      protected ModuleSet newModuleSet(ImmutableMap<String, String> environment, FileSystem fs,
          Console console) {
        return skylark.createModuleSet();
      }
    };

    Files.write(
        options.general.getFileSystem().getPath("/work").resolve("copy.bara.sky"),
        "".getBytes(UTF_8));
    ConfigLoaderProvider clp = main.newConfigLoaderProvider(skylark.createModuleSet());

    ConfigLoader loader = clp.newLoader("./copy.bara.sky", "test");
    assertThat(loader.location()).isEqualTo("/work/copy.bara.sky");
  }

  @Test
  public void testLoadFromStdin() throws IOException, ValidationException {
    String configContent =
        """
        core.workflow(
            name = "default",
            origin = git.origin(url = "https://example.com/orig", ref = "main"),
            destination = folder.destination(),
            authoring = authoring.pass_thru("Copybara <no-reply@google.com>"),
        )
        """;
    options.general.setLoadFromStdinForTest(true);
    Main main =
        new Main(ImmutableMap.of("HOME", Files.createTempDirectory("foo").toString())) {
          @Override
          protected InputStream getStdin() {
            return new ByteArrayInputStream(configContent.getBytes(UTF_8));
          }
        };

    ConfigLoaderProvider clp = main.newConfigLoaderProvider(skylark.createModuleSet());
    ConfigLoader loader = clp.newLoader("<stdin>/copy.bara.sky", null);
    assertThat(loader.location()).isEqualTo("<stdin>/copy.bara.sky");

    Config config = loader.load(options.general.console());
    assertThat(config.getLocation()).isEqualTo("<stdin>/copy.bara.sky");
    assertThat(config.getMigrations()).containsKey("default");
  }

  @Test
  public void testLoadFromStdinParseErrorColorsErrorLineInRed()
      throws IOException, ValidationException {
    String configContent =
        """
        # Line 1
        # Line 2
        core.workflow(
            name = "default",
            origin = git.origin(
                invalid syntax here
            ),
            destination = folder.destination(),
        )
        # Line 10\
        """;
    options.general.setLoadFromStdinForTest(true);
    Main main =
        new Main(ImmutableMap.of("HOME", Files.createTempDirectory("foo").toString())) {
          @Override
          protected InputStream getStdin() {
            return new ByteArrayInputStream(configContent.getBytes(UTF_8));
          }
        };

    ByteArrayOutputStream consoleOut = new ByteArrayOutputStream();
    AnsiConsole ansiConsole =
        new AnsiConsole(
            new ByteArrayInputStream(new byte[0]),
            new PrintStream(consoleOut, true, UTF_8),
            /* verbose= */ false);

    ConfigLoaderProvider clp = main.newConfigLoaderProvider(skylark.createModuleSet());
    ConfigLoader loader = clp.newLoader("<stdin>/copy.bara.sky", null);

    assertThrows(ValidationException.class, () -> loader.load(ansiConsole));

    String output = consoleOut.toString(UTF_8);
    assertThat(output).contains("<stdin>/copy.bara.sky:6:");
    assertThat(output).doesNotContain("# Line 1");
    assertThat(output).doesNotContain("# Line 2");
    assertThat(output).doesNotContain("# Line 10");
    assertThat(output)
        .contains(
            "core.workflow(\n"
                + "    name = \"default\",\n"
                + "    origin = git.origin(\n"
                + ansiConsole.colorize(AnsiColor.RED, "        invalid syntax here")
                + "\n    ),\n"
                + "    destination = folder.destination(),\n"
                + ")");
  }

  @Test
  public void testLoadFromStdinEvalErrorColorsErrorLineInRed()
      throws IOException, ValidationException {
    String configContent =
        """
        # Line 1 comment
        core.workflow(
            name = "default",
            unknown_param = "bad",
        )\
        """;
    options.general.setLoadFromStdinForTest(true);
    Main main =
        new Main(ImmutableMap.of("HOME", Files.createTempDirectory("foo").toString())) {
          @Override
          protected InputStream getStdin() {
            return new ByteArrayInputStream(configContent.getBytes(UTF_8));
          }
        };

    ByteArrayOutputStream consoleOut = new ByteArrayOutputStream();
    AnsiConsole ansiConsole =
        new AnsiConsole(
            new ByteArrayInputStream(new byte[0]),
            new PrintStream(consoleOut, true, UTF_8),
            /* verbose= */ false);

    ConfigLoaderProvider clp = main.newConfigLoaderProvider(skylark.createModuleSet());
    ConfigLoader loader = clp.newLoader("<stdin>/copy.bara.sky", null);

    assertThrows(ValidationException.class, () -> loader.load(ansiConsole));

    String output = consoleOut.toString(UTF_8);
    assertThat(output)
        .contains(
            "# Line 1 comment\n"
                + ansiConsole.colorize(AnsiColor.RED, "core.workflow(")
                + "\n    name = \"default\",\n"
                + "    unknown_param = \"bad\",\n"
                + ")");
  }

  @Test
  public void testLoadFromStdinMultipleSyntaxErrorsPrintsEachContext()
      throws IOException, ValidationException {
    String configContent =
        """
        x = undefined_var_one
        # filler 1
        # filler 2
        # filler 3
        # filler 4
        # filler 5
        # filler 6
        # filler 7
        y = undefined_var_two\
        """;
    options.general.setLoadFromStdinForTest(true);
    Main main =
        new Main(ImmutableMap.of("HOME", Files.createTempDirectory("foo").toString())) {
          @Override
          protected InputStream getStdin() {
            return new ByteArrayInputStream(configContent.getBytes(UTF_8));
          }
        };

    ByteArrayOutputStream consoleOut = new ByteArrayOutputStream();
    AnsiConsole ansiConsole =
        new AnsiConsole(
            new ByteArrayInputStream(new byte[0]),
            new PrintStream(consoleOut, true, UTF_8),
            /* verbose= */ false);

    ConfigLoaderProvider clp = main.newConfigLoaderProvider(skylark.createModuleSet());
    ConfigLoader loader = clp.newLoader("<stdin>/copy.bara.sky", null);

    assertThrows(ValidationException.class, () -> loader.load(ansiConsole));

    String output = consoleOut.toString(UTF_8);
    assertThat(output)
        .contains(
            ansiConsole.colorize(AnsiColor.RED, "x = undefined_var_one")
                + "\n# filler 1\n# filler 2\n# filler 3");
    assertThat(output)
        .contains(
            "# filler 5\n# filler 6\n# filler 7\n"
                + ansiConsole.colorize(AnsiColor.RED, "y = undefined_var_two"));
  }

  @Test
  public void testEvalErrorWithWrappedCausePrintsContext()
      throws IOException, ValidationException {
    String configContent =
        """
        # Line 1 comment
        pattern = re2.compile("[unclosed")
        # Line 3 comment\
        """;
    options.general.setLoadFromStdinForTest(true);
    Main main =
        new Main(ImmutableMap.of("HOME", Files.createTempDirectory("foo").toString())) {
          @Override
          protected InputStream getStdin() {
            return new ByteArrayInputStream(configContent.getBytes(UTF_8));
          }
        };

    ByteArrayOutputStream consoleOut = new ByteArrayOutputStream();
    AnsiConsole ansiConsole =
        new AnsiConsole(
            new ByteArrayInputStream(new byte[0]),
            new PrintStream(consoleOut, true, UTF_8),
            /* verbose= */ false);

    ConfigLoaderProvider clp = main.newConfigLoaderProvider(skylark.createModuleSet());
    ConfigLoader loader = clp.newLoader("<stdin>/copy.bara.sky", null);

    assertThrows(ValidationException.class, () -> loader.load(ansiConsole));

    String output = consoleOut.toString(UTF_8);
    assertThat(output)
        .contains(
            "# Line 1 comment\n"
                + ansiConsole.colorize(AnsiColor.RED, "pattern = re2.compile(\"[unclosed\")")
                + "\n# Line 3 comment");
  }

  @Test
  public void testConfigReadFailureOnErrorDoesNotMaskValidationException() {
    ConfigFile flakyConfigFile =
        new ConfigFile() {
          private boolean firstRead = true;

          @Override
          public ConfigFile resolve(String path) throws CannotResolveLabel {
            throw new CannotResolveLabel("Unsupported");
          }

          @Override
          public String path() {
            return "flaky/copy.bara.sky";
          }

          @Override
          public String getIdentifier() {
            return path();
          }

          @Override
          public byte[] readContentBytes() throws IOException {
            if (firstRead) {
              firstRead = false;
              return "x = undefined_var\n".getBytes(UTF_8);
            }
            throw new IOException("Simulated disk failure while reading error context");
          }
        };

    ConfigLoader loader =
        new ConfigLoader(
            skylark.createModuleSet(), flakyConfigFile, options.general.getStarlarkMode());

    assertThrows(ValidationException.class, () -> loader.load(options.general.console()));
  }

  @Test
  public void testInitEnvironmentCalled() throws IOException {
    ImmutableMap<String, String> envWithHome = ImmutableMap.of("HOME",
        Files.createTempDirectory("foo").toString());

    Main main =
        new Main(envWithHome) {
          @Override
          protected void configureLog(FileSystem fs, String[] args) {}

          @Override
          protected void initEnvironment(Options options, CopybaraCmd copybaraCmd,
              ImmutableList<String> rawArgs) {
            called = true;
          }
        };
    main.run(args);
    assertThat(called).isTrue();
  }

  @Test
  public void testShutdownCalled() {
    Main main =
        new Main() {
          @Override
          protected void configureLog(FileSystem fs, String[] args) {}

          @Override
          protected void shutdown(CommandResult result) {
            called = true;
          }
        };
    main.run(args);
    assertThat(called).isTrue();
  }

  @Test
  public void testInvalidForcedAuthor() throws IOException {
    ImmutableMap<String, String> envWithHome =
        ImmutableMap.of("HOME", Files.createTempDirectory("foo").toString());
    assertThat(new Main(envWithHome).run(/*no arguments*/ new String[] {"--force-author='fdsfds'"}))
        .isEqualTo(ExitCode.COMMAND_LINE_ERROR);
  }

  @Test
  public void testFindFlagValue() {
    String[] args = {"--foo", "bar", "--baz=qux", "--flag_with_empty=", "--flag_at_end=val"};

    assertThat(Main.findFlagValue(args, "--foo").orElse(null)).isEqualTo("bar");
    assertThat(Main.findFlagValue(args, "--baz").orElse(null)).isEqualTo("qux");
    assertThat(Main.findFlagValue(args, "--flag_with_empty").orElse(null)).isEqualTo("");
    assertThat(Main.findFlagValue(args, "--flag_at_end").orElse(null)).isEqualTo("val");
    assertThat(Main.findFlagValue(args, "--missing").isPresent()).isFalse();

    assertThat(Main.findFlagValue(new String[] {"--foo"}, "--foo").isPresent()).isFalse();
    assertThat(Main.findFlagValue(new String[] {"--foo", "--bar"}, "--foo").isPresent()).isFalse();
  }
}
