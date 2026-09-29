/*
 * Copyright (C) 2026 Google LLC
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

package com.google.copybara.onboard.ai;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.copybara.CommandEnv;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.FakeLlmClient;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.util.ExitCode;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AiOnboardCmdTest {

  private static final String ARCHETYPE = "# Test archetype\nUse core.workflow.";

  private static final String CONFIG =
      """
      core.workflow(
          name = "default",
          origin = git.origin(url = "https://github.com/example/origin"),
          destination = git.destination(url = "https://github.com/example/dest"),
          authoring = authoring.pass_thru("Example <example@example.com>"),
      )
      """;

  private final OptionsBuilder optionsBuilder = new OptionsBuilder();
  private final FakeLlmClient llmClient = new FakeLlmClient();
  private TestingConsole console;
  private Path cwd;
  private AiOnboardCmd cmd;

  @Before
  public void setUp() throws Exception {
    cwd = Files.createTempDirectory("ai_onboard");
    console = new TestingConsole();
    optionsBuilder.setConsole(console).setWorkdirToRealTempDir(cwd.toString());
    optionsBuilder.aiOnboard =
        new AiOnboardOptions() {
          @Override
          public LlmClient getLlmClient() {
            return llmClient;
          }
        };
    cmd = new AiOnboardCmd(ARCHETYPE);
  }

  @Test
  public void failsWithoutLlmBackend() {
    optionsBuilder.aiOnboard = new AiOnboardOptions();

    ValidationException e = assertThrows(ValidationException.class, () -> run());

    assertThat(e).hasMessageThat().contains("No LLM backend is configured");
  }

  @Test
  public void writesConfigFromFencedResponse() throws Exception {
    answerSurvey();
    llmClient.respondWith("Here is your config:\n```starlark\n" + CONFIG + "```\nGood luck!");

    ExitCode exit = run();

    assertThat(exit).isEqualTo(ExitCode.SUCCESS);
    assertThat(readOutput(AiOnboardCmd.DEFAULT_OUTPUT_FILE)).isEqualTo(CONFIG);
    console.assertThat().onceInLog(MessageType.PROGRESS, "Generating Copybara config\\.\\.\\.");
  }

  @Test
  public void writesToNestedPathGivenAsArgument() throws Exception {
    answerSurvey();
    llmClient.respondWith(CONFIG);

    assertThat(run("new/dir/my.bara.sky")).isEqualTo(ExitCode.SUCCESS);

    assertThat(readOutput("new/dir/my.bara.sky")).isEqualTo(CONFIG);
  }

  @Test
  public void promptContainsArchetypeAndSurveyAnswers() throws Exception {
    answerSurvey();
    llmClient.respondWith(CONFIG);

    assertThat(run()).isEqualTo(ExitCode.SUCCESS);

    assertThat(llmClient.getPrompts()).hasSize(1);
    String prompt = llmClient.getPrompts().get(0);
    assertThat(prompt).startsWith(ARCHETYPE);
    assertThat(prompt).contains("origin_path: \"third_party/example\"");
    assertThat(prompt).contains("destination_path: \"https://github.com/example/dest\"");
    assertThat(prompt).contains("destination_branch: \"release\"");
    assertThat(prompt).contains("default_author: \"Example <example@example.com>\"");
    assertThat(prompt).contains("desired_origin_files: \"everything except tests\"");
    assertThat(prompt).contains("desired_destination_files: \"everything except docs\"");
    assertThat(prompt).contains("desired_transformations: \"move the package to the root\"");
    assertThat(prompt).contains("other_requests: \"import GitHub PRs\"");
  }

  @Test
  public void llmFailureReturnsEnvironmentErrorAndWritesNothing() throws Exception {
    answerSurvey();
    llmClient.failWith(new LlmException("backend unavailable"));

    ExitCode exit = run();

    assertThat(exit).isEqualTo(ExitCode.ENVIRONMENT_ERROR);
    assertThat(Files.exists(cwd.resolve(AiOnboardCmd.DEFAULT_OUTPUT_FILE))).isFalse();
    console
        .assertThat()
        .onceInLog(MessageType.ERROR, "Failed to generate config: backend unavailable");
  }

  @Test
  public void emptyResponseReturnsConfigurationErrorAndWritesNothing() throws Exception {
    answerSurvey();
    llmClient.respondWith("```starlark\n\n```");

    ExitCode exit = run();

    assertThat(exit).isEqualTo(ExitCode.CONFIGURATION_ERROR);
    assertThat(Files.exists(cwd.resolve(AiOnboardCmd.DEFAULT_OUTPUT_FILE))).isFalse();
  }

  @Test
  public void existingFileIsKeptWhenUserDeclines() throws Exception {
    Path output = cwd.resolve(AiOnboardCmd.DEFAULT_OUTPUT_FILE);
    Files.writeString(output, "# original\n", UTF_8);
    console.respondNo();

    ExitCode exit = run();

    assertThat(exit).isEqualTo(ExitCode.NO_OP);
    assertThat(Files.readString(output, UTF_8)).isEqualTo("# original\n");
    // Declining happens before the survey, so the model is never called.
    assertThat(llmClient.getPrompts()).isEmpty();
  }

  @Test
  public void invalidAuthorIsRejectedAndAskedAgain() throws Exception {
    console
        .respondWithString("third_party/example")
        .respondWithString("https://github.com/example/dest")
        .respondWithString("")
        .respondWithString("not an author")
        .respondWithString("Example <example@example.com>")
        .respondWithString("")
        .respondWithString("")
        .respondWithString("")
        .respondWithString("");
    llmClient.respondWith(CONFIG);

    assertThat(run()).isEqualTo(ExitCode.SUCCESS);

    console.assertThat().onceInLog(MessageType.ERROR, "Invalid author\\..*");
    String prompt = llmClient.getPrompts().get(0);
    assertThat(prompt).contains("default_author: \"Example <example@example.com>\"");
    // Empty answers use the defaults.
    assertThat(prompt).contains("destination_branch: \"main\"");
    assertThat(prompt).contains("other_requests: \"none\"");
  }

  @Test
  public void name() {
    assertThat(cmd.name()).isEqualTo("ai_onboard");
  }

  private void answerSurvey() {
    console
        .respondWithString("third_party/example")
        .respondWithString("https://github.com/example/dest")
        .respondWithString("release")
        .respondWithString("Example <example@example.com>")
        .respondWithString("everything except tests")
        .respondWithString("everything except docs")
        .respondWithString("move the package to the root")
        .respondWithString("import GitHub PRs");
  }

  private ExitCode run(String... args) throws Exception {
    return cmd.run(new CommandEnv(cwd, optionsBuilder.build(), ImmutableList.copyOf(args)));
  }

  private String readOutput(String relativePath) throws Exception {
    return Files.readString(cwd.resolve(relativePath), UTF_8);
  }
}
