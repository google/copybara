package com.google.copybara.config;

import java.io.IOException;

/**
 * An object representing a configuration file and that it can be used to resolve
 * other config files relative to this one.
 */
public interface ConfigFile {

  /**
   * Resolve {@code label} relative to the current config file.
   *
   * @throws CannotResolveLabel if the label cannot be resolved to a content
   */
  ConfigFile resolve(String label) throws CannotResolveLabel;

  /**
   * Resolved, non-relative name of the config file.
   */
  String path();

  /**
   * Get the contents of the file.
   *
   * <p>Implementations of this interface should prefer to not eagerly load the content of this
   * method is call in order to allow the callers to check its own cache if they already have
   * {@link #path()} path.
   */
  byte[] content() throws IOException;
}
