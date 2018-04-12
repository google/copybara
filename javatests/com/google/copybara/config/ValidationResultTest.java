/*
 * Copyright (C) 2018 Google Inc.
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

import com.google.common.collect.ImmutableList;
import com.google.copybara.config.ValidationResult.ValidationMessage;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ValidationResultTest {

  @Test
  public void testValidationResult() {
    ValidationResult.Builder resultBuilder = new ValidationResult.Builder();
    resultBuilder.warning("This is a warning");
    resultBuilder.warningFmt("This is a warning with %s", "format");
    resultBuilder.error("This is an error");
    resultBuilder.errorFmt("This is an error with %s", "format");


    ValidationResult.Builder otherBuilder = new ValidationResult.Builder();
    otherBuilder.warning("This is another warning");
    otherBuilder.error("This is another error");

    resultBuilder.append(otherBuilder.build());
    ValidationResult result = resultBuilder.build();

    assertThat(result.hasWarnings()).isTrue();
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.getWarnings())
        .containsExactly(
            "This is a warning", "This is a warning with format", "This is another warning");
    assertThat(result.getErrors())
        .containsExactly(
            "This is an error", "This is an error with format", "This is another error");

    // Validate that order is preserved
    List<String> allMessages = result.getAllMessages().stream()
        .map(ValidationMessage::getMessage)
        .collect(ImmutableList.toImmutableList());
    assertThat(allMessages).hasSize(6);
    assertThat(allMessages)
        .isEqualTo(
            ImmutableList.of(
                "This is a warning",
                "This is a warning with format",
                "This is an error",
                "This is an error with format",
                "This is another warning",
                "This is another error"));
  }
}
