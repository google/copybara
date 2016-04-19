package com.google.copybara.doc.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Documentation for elements of Copybara configuration, like Origins, Destinations, etc.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface DocElement {

  /**
   * Name used in the YAML configuration files. Like "!MyComponent".
   */
  String yamlName();

  /**
   * Text explaining what the element does and how to use.
   */
  String description();

  /**
   * Kind of the element, can be Origin, Destination, etc.
   */
  Class<?> elementKind();

  /**
   * An associated flags class annotated with {@code Parameter}
   */
  Class<?> flags() default Object.class;
}
