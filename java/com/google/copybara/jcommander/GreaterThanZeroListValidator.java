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

package com.google.copybara.jcommander;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.ParameterException;
import com.google.common.base.Splitter;

/** Check that a list of integers is equal or greater than zero */
public class GreaterThanZeroListValidator implements IParameterValidator {

  @Override
  public void validate(String name, String value) throws ParameterException {
    String delimiter = value.contains(",") ? "," : ";";
    for (String element : Splitter.on(delimiter).split(value)) {
      if (Integer.parseInt(element) < 1) {
        throw new ParameterException(
            String.format(
                "Parameter %s elements should be greater than zero (found %s)", name, element));
      }
    }
  }
}
