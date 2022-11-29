/*
 * Copyright (C) 2022 Google Inc.
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

package com.google.copybara.onboard.core.template;

import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.onboard.core.CannotProvideException;
import com.google.copybara.onboard.core.Input;
import com.google.copybara.onboard.core.InputProviderResolver;
import com.google.copybara.onboard.core.template.Field.Location;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

/**
 * A config generator that users a template for generating the config. Template fields can be in
 * two forms:
 * - NAMED fields: Text like "::field_name::" is replaced with the value
 * - KEYWORD fields: If the template has the literal "::keyword_params::", it will replace it
 * with a list of the keyword params.
 */
public abstract class TemplateConfigGenerator implements ConfigGenerator {

  private static final Pattern NAMED_FIELD = Pattern.compile("::[A-Za-z0-9_-]+::");
  private static final Pattern KEYWORD = Pattern.compile("([\t ]*)::keyword_params::");
  private final String template;

  public TemplateConfigGenerator(String template) {
    this.template = template;
  }

  @Override
  public String generate(InputProviderResolver resolver)
      throws CannotProvideException, InterruptedException {

    ImmutableSet<Input<?>> consumes = consumes();
    ImmutableMap<Field, Object> fields = resolve(new InputProviderResolver() {
      @Override
      public <T> Optional<T> resolve(Input<T> input)
          throws InterruptedException, CannotProvideException {
        if (!consumes.contains(input)) {
          throw new IllegalStateException(
              String.format("Non-declared input in template: %s. Add it to consumes() method",
                  input));
        }
        return resolver.resolve(input);
      }
    });
    String config = template;
    for (Entry<Field, Object> e : fields.entrySet()) {
      if (e.getKey().location() == Location.NAMED) {
        config = setNamedParam(config, e.getKey(), e.getValue());
      }
    }
    Matcher keywordMatcher = KEYWORD.matcher(config);
    if (keywordMatcher.find()) {
      String spaces = keywordMatcher.group(1);
      config = keywordMatcher.replaceFirst(
          fields.keySet().stream()
              .filter(x -> x.location() == Location.KEYWORD)
              .map(x -> String.format("%s%s = %s,", spaces, x.name(), fields.get(x)))
              .collect(joining("\n")));
    }
    Matcher matcher = NAMED_FIELD.matcher(config);
    Set<String> notReplaced = new HashSet<>();
    while (matcher.find()) {
      notReplaced.add(matcher.group());
    }
    if (!notReplaced.isEmpty()) {
      throw new IllegalStateException(
          "The following template variables are not being set with values: " + notReplaced);
    }

    return config;
  }

  private String setNamedParam(String config, Field field, Object value) {
    if (!config.contains(field.name())) {
      throw new IllegalStateException(
          String.format(
              "Named parameter %s not used in this template. Consider using"
                  + " setStringKeywordParameter instead.",
              field.name()));
    }
    String replace = config.replace(
        String.format("::%s::", field.name()), String.format("%s", value));

    if (field.required() && replace.equals(config)) {
      throw new IllegalStateException(String.format("::%s:: not found in template", field.name()));
    }
    return replace;
  }

  /**
   * Useful for keyword fields that we want to represent them as string literals.
   *
   * {@link #resolve(InputProviderResolver)} can return a value wrapped like this for keyword
   * field values that we want them to be printed as foo = "value" (with quotes).
   */
  protected String keywordStringLiteral(String value) {
    return "\"" + value + "\"";
  }

  /**
   * Method to be implemented by the specific templates to provide the field values using
   * {@link InputProviderResolver}.
   */
  protected abstract ImmutableMap<Field, Object> resolve(InputProviderResolver resolver)
      throws InterruptedException, CannotProvideException;
}
