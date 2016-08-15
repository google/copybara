package com.google.copybara.config;

import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;

/**
 * A {@link SkylarkModule} that implements this interface will be given the current config file
 * which can be used to load content given labels.
 */
public interface LabelsAwareModule {

  /**
   * Called before invoking any methods on a module in order to give the module access to the
   * current config file. This may be called multiple times, in which case only the most recent
   * {@link ConfigFile} should be used.
   *
   * TODO(matvore): Figure out how this works with concurrent loading.
   */
  void setConfigFile(ConfigFile configFile);
}
