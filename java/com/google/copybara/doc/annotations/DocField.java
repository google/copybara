package com.google.copybara.doc.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A field documentation for a {@link DocElement} type.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface DocField {

  String description();

  boolean required() default true;

  String defaultValue() default "none";

  boolean undocumented() default false;

  boolean deprecated() default false;

  /**
   * Use when the elements of a list fields are always of the same type so that we can avoid
   * using !FieldClass.
   */
  Class<?> listType() default Object.class;
}
