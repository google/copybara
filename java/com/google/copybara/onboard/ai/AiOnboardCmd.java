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

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.beust.jcommander.Parameters;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.copybara.CommandEnv;
import com.google.copybara.CopybaraCmd;
import com.google.copybara.GeneralOptions;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.onboard.core.AskInputProvider.Mode;
import com.google.copybara.onboard.core.CannotConvertException;
import com.google.copybara.onboard.core.CannotProvideException;
import com.google.copybara.onboard.core.InputProvider;
import com.google.copybara.onboard.core.InputProviderResolver;
import com.google.copybara.onboard.core.InputProviderResolverImpl;
import com.google.copybara.util.ExitCode;
import com.google.copybara.util.console.Console;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/** Generates a Copybara config from a short survey by asking an LLM to write the Starlark. */
@Parameters(
    separators = "=",
    commandDescription =
        "EXPERIMENTAL - Generates a Copybara config from a short survey using an LLM.")
public class AiOnboardCmd implements CopybaraCmd {

  static final String DEFAULT_OUTPUT_FILE = "copy.bara.sky";

  private static final Pattern FENCED_BLOCK =
      Pattern.compile("```(?:starlark|python|sky|bzl)?\\s*\\n(.*?)```", Pattern.DOTALL);

  private final String archetype;

  /**
   * @param archetype Markdown describing the target workflow macro, its parameters, and examples
   */
  public AiOnboardCmd(String archetype) {
    this.archetype = checkNotNull(archetype);
  }

  @Override
  public ExitCode run(CommandEnv commandEnv) throws ValidationException, IOException {
    LlmClient llmClient = commandEnv.getOptions().get(AiOnboardOptions.class).getLlmClient();
    GeneralOptions generalOptions = commandEnv.getOptions().get(GeneralOptions.class);
    Console console = generalOptions.console();
    Path outputPath = resolveOutputPath(commandEnv, generalOptions);

    if (Files.exists(outputPath)
        && !console.promptConfirmationFmt("'%s' already exists. Overwrite it?", outputPath)) {
      console.warnFmt("Not overwriting '%s'.", outputPath);
      return ExitCode.NO_OP;
    }

    ImmutableMap<String, String> answers;
    try {
      answers =
          askSurvey(
              InputProviderResolverImpl.create(
                  getInputProviders(commandEnv),
                  (value, resolver) -> {
                    throw new CannotConvertException("Starlark values are not supported.");
                  },
                  Mode.CONFIRM,
                  console));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      console.error("Interrupted: " + e.getMessage());
      return ExitCode.INTERRUPTED;
    } catch (CannotProvideException e) {
      console.error("Cannot resolve input field: " + e.getMessage());
      return ExitCode.COMMAND_LINE_ERROR;
    }

    String response;
    try {
      console.progress("Generating Copybara config...");
      response = llmClient.generate(buildPrompt(answers));
    } catch (LlmException e) {
      console.errorFmt("Failed to generate config: %s", e.getMessage());
      return ExitCode.ENVIRONMENT_ERROR;
    }

    Optional<String> config = extractStarlark(response);
    if (config.isEmpty()) {
      console.error("The model returned an empty response. No config was written.");
      return ExitCode.CONFIGURATION_ERROR;
    }

    Files.createDirectories(outputPath.getParent());
    Files.writeString(outputPath, config.get(), UTF_8);
    console.infoFmt(
        "Wrote generated config to '%s'. Review it and run 'copybara validate %s' before use.",
        outputPath, outputPath);
    return ExitCode.SUCCESS;
  }

  /**
   * Returns providers that can infer survey answers. Answers without a provider are asked to the
   * user.
   *
   * <p>Subclasses can override this to infer environment-specific answers (e.g. depot paths).
   */
  protected ImmutableList<InputProvider> getInputProviders(CommandEnv commandEnv) {
    return ImmutableList.of();
  }

  /**
   * Resolves what the user wants to migrate, returns the answers in order, keyed by the field name
   * used in the prompt.
   */
  protected ImmutableMap<String, String> askSurvey(InputProviderResolver resolver)
      throws InterruptedException, CannotProvideException {
    return ImmutableMap.<String, String>builder()
        .put("origin_path", resolver.resolve(AiInputs.ORIGIN_PATH))
        .put("destination_path", resolver.resolve(AiInputs.DESTINATION_URL))
        .put("destination_branch", resolver.resolve(AiInputs.DESTINATION_BRANCH))
        .put("default_author", resolver.resolve(AiInputs.DEFAULT_AUTHOR).toString())
        .put("desired_origin_files", resolver.resolve(AiInputs.ORIGIN_FILES))
        .put("desired_destination_files", resolver.resolve(AiInputs.DESTINATION_FILES))
        .put("desired_transformations", resolver.resolve(AiInputs.TRANSFORMATIONS))
        .put("other_requests", resolver.resolve(AiInputs.OTHER_REQUESTS))
        .buildOrThrow();
  }

  /** Builds the prompt: the archetype followed by the user's answers. */
  String buildPrompt(Map<String, String> answers) {
    StringBuilder prompt =
        new StringBuilder(archetype).append("\n\n---\n## User Onboarding Input\n");
    answers.forEach((field, answer) -> prompt.append(String.format("%s: \"%s\"\n", field, answer)));
    return prompt.toString();
  }

  /**
   * Returns the contents of the first fenced code block in {@code response}. If there is no fenced
   * block, the whole response is assumed to be Starlark. Returns empty if the response is blank.
   */
  static Optional<String> extractStarlark(String response) {
    Matcher matcher = FENCED_BLOCK.matcher(response);
    String code = (matcher.find() ? matcher.group(1) : response).strip();
    return code.isEmpty() ? Optional.empty() : Optional.of(code + "\n");
  }

  /** Uses the first positional argument if given, otherwise {@code ./copy.bara.sky}. */
  private static Path resolveOutputPath(CommandEnv commandEnv, GeneralOptions generalOptions) {
    String fileName = Iterables.getFirst(commandEnv.getArgs(), DEFAULT_OUTPUT_FILE);
    return generalOptions.getCwd().resolve(fileName).normalize();
  }

  @Override
  public String name() {
    return "ai_onboard";
  }
}
