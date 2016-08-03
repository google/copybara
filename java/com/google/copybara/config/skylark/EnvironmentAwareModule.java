package com.google.copybara.config.skylark;

import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;

import java.util.Map;

import javax.annotation.Nullable;

/**
 * A {@link SkylarkModule} that implements this interface will be initialized with the given
 * environment.
 *
 * <p>This method will be invoked just after registering the namespace objects in Skylark.
 */
public interface EnvironmentAwareModule {

  /**
   * Sets the environment to the module.
   */
  void setEnvironment(@Nullable Map<String, String> environment);
}
