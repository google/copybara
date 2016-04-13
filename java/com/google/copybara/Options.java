package com.google.copybara;

import com.google.common.collect.ImmutableMap;

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
    Option config = this.config.get(optionClass);
    if (config == null) {
      throw new IllegalStateException("No option type found for " + optionClass);
    }
    return (T) config;
  }
}
