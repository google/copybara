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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.copybara.exception.ValidationException.checkCondition;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.beust.jcommander.Parameters;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.copybara.CommandEnv;
import com.google.copybara.ConfigLoaderProvider;
import com.google.copybara.CopybaraCmd;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.ValidateCmd;
import com.google.copybara.config.ConfigValidator;
import com.google.copybara.config.ValidationResult;
import com.google.copybara.config.ValidationResult.ValidationMessage;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.onboard.GeneratorOptions;
import com.google.copybara.onboard.core.CannotConvertException;
import com.google.copybara.onboard.core.CannotProvideException;
import com.google.copybara.onboard.core.InputProvider;
import com.google.copybara.onboard.core.InputProviderResolver;
import com.google.copybara.onboard.core.InputProviderResolverImpl;
import com.google.copybara.onboard.core.MapBasedInputProvider;
import com.google.copybara.util.ExitCode;
import com.google.copybara.util.console.CapturingConsole;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.Message;
import com.google.copybara.util.console.Message.MessageType;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

/** Generates a Copybara config from a short survey by asking an LLM to write the Starlark. */
@Parameters(
    separators = "=",
    commandDescription =
        "EXPERIMENTAL - Generates a Copybara config from a short survey using an LLM.")
public class AiOnboardCmd implements CopybaraCmd {

  static final String DEFAULT_OUTPUT_FILE = "copy.bara.sky";

  private static final Pattern FENCED_BLOCK =
      Pattern.compile("```(?:starlark|python|sky|bzl)?\\s*\\n(.*?)```", Pattern.DOTALL);

  @Nullable private final ConfigValidator configValidator;
  @Nullable private final ConfigLoaderProvider configLoaderProvider;

  /** Writes the generated config without validating it. */
  public AiOnboardCmd() {
    this.configValidator = null;
    this.configLoaderProvider = null;
  }

  /** Validates the generated config after writing it, like {@code copybara validate}. */
  public AiOnboardCmd(ConfigValidator configValidator, ConfigLoaderProvider configLoaderProvider) {
    this.configValidator = checkNotNull(configValidator);
    this.configLoaderProvider = checkNotNull(configLoaderProvider);
  }

  @Override
  public ExitCode run(CommandEnv commandEnv) throws ValidationException, IOException {
    AiOnboardOptions aiOnboardOptions = commandEnv.getOptions().get(AiOnboardOptions.class);
    checkCondition(
        aiOnboardOptions.maxRepairAttempts >= 0,
        "--ai-onboard-max-repair-attempts must not be negative, but was %s",
        aiOnboardOptions.maxRepairAttempts);
    LlmClient llmClient = aiOnboardOptions.getLlmClient();
    String archetype = aiOnboardOptions.getArchetype();
    GeneralOptions generalOptions = commandEnv.getOptions().get(GeneralOptions.class);
    Console console = generalOptions.console();
    Path outputPath = resolveOutputPath(commandEnv, generalOptions);

    if (Files.exists(outputPath)
        && !console.promptConfirmationFmt("'%s' already exists. Overwrite it?", outputPath)) {
      console.warnFmt("Not overwriting '%s'.", outputPath);
      return ExitCode.NO_OP;
    }

    // Registers the inputs so that --inputs can refer to them by name.
    var _ = AiInputs.all();
    ImmutableMap<String, String> answers;
    try {
      answers =
          askSurvey(
              InputProviderResolverImpl.create(
                  getInputProviders(commandEnv),
                  (value, resolver) -> {
                    throw new CannotConvertException("Starlark values are not supported.");
                  },
                  commandEnv.getOptions().get(GeneratorOptions.class).askMode,
                  console));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      console.error("Interrupted: " + e.getMessage());
      return ExitCode.INTERRUPTED;
    } catch (CannotProvideException e) {
      console.error("Cannot resolve input field: " + e.getMessage());
      return ExitCode.COMMAND_LINE_ERROR;
    }

    String repoContext = aiOnboardOptions.getRepoContext(answers).orElse(null);
    String response;
    try {
      console.progress("Generating Copybara config...");
      response = llmClient.generate(buildPrompt(archetype, answers, repoContext));
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
    if (configLoaderProvider == null) {
      console.infoFmt(
          "Wrote generated config to '%s'. Review it and run 'copybara validate %s' before use.",
          outputPath, outputPath);
      return ExitCode.SUCCESS;
    }

    ImmutableList<String> errors = validate(commandEnv, outputPath, console);
    Optional<RepairPolicy> repairPolicy = aiOnboardOptions.getRepairPolicy();
    String currentConfig = config.get();
    int maxAttempts = aiOnboardOptions.maxRepairAttempts;
    for (int attempt = 1;
        !errors.isEmpty() && repairPolicy.isPresent() && attempt <= maxAttempts;
        attempt++) {
      Optional<String> repaired =
          repair(
              repairPolicy.get(),
              new RepairPolicy.Request(archetype, answers, currentConfig, errors),
              attempt,
              maxAttempts,
              console);
      if (repaired.isEmpty()) {
        break;
      }
      Files.writeString(outputPath, repaired.get(), UTF_8);
      ImmutableList<String> repairedErrors = validate(commandEnv, outputPath, console);
      if (repairedErrors.equals(errors)) {
        console.warn("The fixed config fails with the same errors. Not trying again.");
        break;
      }
      errors = repairedErrors;
      currentConfig = repaired.get();
    }
    if (!errors.isEmpty()) {
      console.errorFmt(
          "Wrote generated config to '%s', but it isn't valid. Fix the errors above, or run"
              + " ai_onboard again.",
          outputPath);
      return ExitCode.CONFIGURATION_ERROR;
    }
    console.infoFmt("Wrote a valid config to '%s'. Review it before use.", outputPath);
    return ExitCode.SUCCESS;
  }

  /**
   * Validates the config at {@code outputPath} and prints the messages. Returns every error,
   * including the ones the loader only prints, like Starlark syntax errors.
   */
  private ImmutableList<String> validate(CommandEnv commandEnv, Path outputPath, Console console)
      throws ValidationException, IOException {
    CapturingConsole capturing = CapturingConsole.captureOnlyConsole(console, MessageType.ERROR);
    GeneralOptions generalOptions =
        commandEnv.getOptions().get(GeneralOptions.class).withConsole(capturing);
    ValidationResult result =
        ValidateCmd.validate(
            new Options(
                commandEnv.getOptions().getAll().stream()
                    .map(option -> option instanceof GeneralOptions ? generalOptions : option)
                    .collect(toImmutableList())),
            configLoaderProvider.newLoader(outputPath.toString(), /* sourceRef= */ null),
            configValidator,
            /* migrationName= */ null);
    for (ValidationMessage message : result.getAllMessages()) {
      switch (message.getLevel()) {
        case WARNING -> console.warn(message.getMessage());
        case ERROR -> console.error(message.getMessage());
      }
    }
    ImmutableSet<String> configLines =
        ImmutableSet.copyOf(Files.readString(outputPath, UTF_8).split("\n", -1));
    return ImmutableList.<String>builder()
        .addAll(
            capturing.getMessages().stream()
                .map(Message::getText)
                .filter(text -> !isConfigExcerpt(text, configLines))
                .iterator())
        .addAll(result.getErrors())
        .build();
  }

  /** Returns whether {@code message} is just config lines, like the context around an error. */
  private static boolean isConfigExcerpt(String message, ImmutableSet<String> configLines) {
    return message
        .lines()
        .allMatch(
            line ->
                (line.startsWith("-> ") || line.startsWith("   "))
                    && configLines.contains(line.substring(3)));
  }

  /**
   * Asks {@code policy} to fix the config in {@code request}. Returns the fixed config, or empty if
   * the user needs to fix it, the model didn't change it, or the model failed.
   */
  private static Optional<String> repair(
      RepairPolicy policy,
      RepairPolicy.Request request,
      int attempt,
      int maxAttempts,
      Console console) {
    try {
      console.progressFmt(
          "Checking whether the errors can be fixed automatically (attempt %d of %d)...",
          attempt, maxAttempts);
      RepairPolicy.Classification classification = policy.classify(request);
      if (!classification.fixable()) {
        console.errorFmt("ai_onboard can't fix this automatically: %s", classification.reason());
        if (!classification.userAction().isEmpty()) {
          console.errorFmt("To fix it: %s", classification.userAction());
        }
        return Optional.empty();
      }
      console.progressFmt("Fixing the config (attempt %d of %d)...", attempt, maxAttempts);
      RepairPolicy.Repair repair = policy.repair(request);
      if (repair.config().isEmpty()) {
        console.errorFmt("ai_onboard can't fix this automatically: %s", repair.reason());
        return Optional.empty();
      }
      Optional<String> fixed = extractStarlark(repair.config().get());
      if (fixed.isEmpty() || fixed.get().equals(request.config())) {
        console.warn("The model didn't change the config. Not trying again.");
        return Optional.empty();
      }
      return fixed;
    } catch (LlmException e) {
      console.warnFmt("Couldn't fix the config automatically: %s", e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * Returns providers that can infer survey answers. Answers without a provider are asked to the
   * user. By default, answers can be given with {@code --inputs}.
   *
   * <p>Subclasses can override this to infer environment-specific answers (e.g. depot paths).
   */
  protected ImmutableList<InputProvider> getInputProviders(CommandEnv commandEnv) {
    return ImmutableList.of(
        new MapBasedInputProvider(
            commandEnv.getOptions().get(GeneratorOptions.class).inputs,
            InputProvider.COMMAND_LINE_PRIORITY));
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

  /** Builds the prompt: the archetype, the user's answers, then the repository context if any. */
  static String buildPrompt(
      String archetype, Map<String, String> answers, @Nullable String repoContext) {
    StringBuilder prompt =
        new StringBuilder(archetype).append("\n\n---\n## User Onboarding Input\n");
    answers.forEach((field, answer) -> prompt.append(String.format("%s: \"%s\"\n", field, answer)));
    if (repoContext != null) {
      prompt.append("\n---\n## Repository Context\n").append(repoContext).append('\n');
    }
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
