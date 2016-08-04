// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.doc.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to associate flags to functions in SkylarkModules.
 *
 * <p>Can be set to a whole module or specific field functions.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
public @interface UsesFlags {

  /**
   * An associated flags class annotated with {@code Parameter}
   */
  // TODO(malcon): change to <? extends Option>.
  Class<?>[] value();
}
