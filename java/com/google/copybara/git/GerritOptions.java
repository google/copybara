// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import com.google.common.base.Strings;
import com.google.copybara.Option;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;

import java.util.regex.Pattern;

/**
 * Arguments for {@link GerritDestination}.
 */
@Parameters(separators = "=")
public final class GerritOptions implements Option {

  private static final Pattern CHANGE_ID_PATTERN = Pattern.compile("I[0-9a-f]{40}");

  public static final class ChangeIdValidator implements IParameterValidator {
    @Override
    public void validate(String name, String value) throws ParameterException {
      if (!Strings.isNullOrEmpty(value) && !CHANGE_ID_PATTERN.matcher(value).matches()) {
        throw new ParameterException(
            String.format("%s value '%s' does not match Gerrit Change ID pattern: %s",
                name, value, CHANGE_ID_PATTERN.pattern()));
      }
    }
  }

  @Parameter(names = "--gerrit-change-id",
      description = "ChangeId to use in the generated commit message",
      validateWith = ChangeIdValidator.class)
  String gerritChangeId = "";
}
