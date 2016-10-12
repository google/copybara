package com.google.copybara.doc.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to associate an example snippet with a configuration element.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
@Repeatable(Examples.class)
public @interface Example {

  /**
   * Title of the example.
   */
  String title();

  /**
   * Description to show before the snippet. For example {@code "Move the root to foo directory"}
   */
  String before();

  /**
   * The code of the snippet. For example {@code "core.move('', 'foo')"}
   */
  String code();

  /**
   * Description to show after the code snippet.
   */
  String after() default "";
}
