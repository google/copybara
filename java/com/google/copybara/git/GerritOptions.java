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

package com.google.copybara.git;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Suppliers;
import com.google.copybara.Option;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Arguments for {@link GerritDestination}. */
@Parameters(separators = "=")
public class GerritOptions implements Option {

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
      description = "ChangeId to use in the generated commit message. Use this flag if you want "
          + "to reuse the same Gerrit review for an export.",
      validateWith = ChangeIdValidator.class)
  String gerritChangeId = "";

  @Parameter(names = "--gerrit-topic", description = "Gerrit topic to use")
  String gerritTopic = "";

  protected GerritChangeFinder changeFinder;

  /** Create options with a specific change finder. */
  protected GerritOptions(GerritChangeFinder changeFinder) {
    this.changeFinder = Preconditions.checkNotNull(changeFinder);
  }

  /** Instantiate options with a null change finder. */
  public GerritOptions() {
    this.changeFinder = null;
  }

  public synchronized Supplier<GerritChangeFinder> getChangeFinder() {
    return Suppliers.memoize(
        new com.google.common.base.Supplier<GerritChangeFinder>() {
          @Override
          public synchronized GerritChangeFinder get() {
            if (changeFinder == null) {
              changeFinder = newChangeFinder();
            }
            return changeFinder;
          }
        });
  }

  /** Override this method in a class for a specific Gerrit implementation. */
  protected GerritChangeFinder newChangeFinder() {
    return new GerritChangeFinder.Default();
  }
}
