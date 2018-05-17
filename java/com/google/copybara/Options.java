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

package com.google.copybara;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import java.util.Map.Entry;

/**
 * A class that groups all the options used in the program
 */
public class Options {

  private final ImmutableMap<Class<? extends Option>, Option> config;

  public Options(Iterable<? extends Option> options) {
    ImmutableMap.Builder<Class<? extends Option>, Option> builder = ImmutableMap.builder();
    for (Option option : options) {
      builder.put(option.getClass(), option);
    }
    config = builder.build();
  }

  /**
   * Get an option for a given class.
   *
   * @throws IllegalStateException if the configuration cannot be found
   */
  @SuppressWarnings("unchecked")
  public <T extends Option> T get(Class<? extends T> optionClass) {
    Option option = config.get(optionClass);
    if (option == null) {
      // If we didn't find the exact class, look for a subclass.
      for (Entry<Class<? extends Option>, Option> entry : config.entrySet()) {
        if (optionClass.isAssignableFrom(entry.getKey())) {
          return (T) entry.getValue();
        }
      }
      throw new IllegalStateException("No option type found for " + optionClass);
    }
    return (T) option;
  }

  public ImmutableCollection<Option> getAll() {
    return config.values();
  }
}
