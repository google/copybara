/*
 * Copyright (C) 2020 Google Inc.
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

import com.google.common.base.Splitter;
import com.google.copybara.util.Glob;

import com.beust.jcommander.IStringConverter;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts strings like some/path/**,other/path,-some/other/exclude/** to a Glob.
 * Elements starting with '-' are excluded. The rest are included. If someone mix
 * foo,-bar,baz, it won't do glob([foo], exclude =[bar]) + glob([baz]) but
 * glob([foo,baz], exclude = [bar]). We might change this in the future if usage makes it worth
 * it.
 */
public class GlobConverter implements IStringConverter<Glob> {

  @Override
  public Glob convert(String value) {
    Iterable<String> split = Splitter.on(",").trimResults().omitEmptyStrings().split(value);
    List<String> include = new ArrayList<>();
    List<String> exclude = new ArrayList<>();
    for (String e : split) {
      if (e.startsWith("-")) {
        exclude.add(e.substring(1));
      } else {
        include.add(e);
      }
    }
    return Glob.createGlob(include, exclude);
  }
}
