package com.google.copybara;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.Arrays;

/**
 * A class that groups all the options used in the program
 */
public class Options {

  private final ImmutableMap<Class<?>, Object> config;

  public Options(ImmutableList<Option> options) {
    ImmutableMap.Builder<Class<?>, Object> builder = ImmutableMap.builder();
    for (Object option : options) {
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
  public <T> T getOption(Class<? extends T> optionClass) {
    Object config = this.config.get(optionClass);
    if (config == null) {
      throw new IllegalStateException("No option type found for " + optionClass);
    }
    return (T) config;
  }
}
