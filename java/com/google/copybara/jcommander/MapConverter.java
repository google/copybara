/*
 * Copyright (C) 2019 Google Inc.
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

import com.beust.jcommander.IStringConverter;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;

/**
 * Convert a String in the form of foo:value1,bar:value2 into an ImmutableMap of Strings.
 */
public class MapConverter implements IStringConverter<ImmutableMap<String, String>> {

  @Override
  public ImmutableMap<String, String> convert(String s) {
    return ImmutableMap
        .copyOf(Splitter.on(',')
            .omitEmptyStrings().trimResults().withKeyValueSeparator(':')
            .split(s));
  }
}
