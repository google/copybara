package com.google.copybara.config.skylark;

import com.google.copybara.Options;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;

/**
 * A {@link SkylarkModule} that implements this interface will be initialized with the options.
 *
 * <p>This method will be invoked just after registering the namespace objects in Skylark.
 */
public interface OptionsAwareModule {

  /**
   * Set the options for the current Copybara run.
   */
  void setOptions(Options options);
}
