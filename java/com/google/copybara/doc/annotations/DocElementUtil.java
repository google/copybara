// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.doc.annotations;

import com.google.common.base.Preconditions;
import com.google.common.base.Verify;

/**
 * Utilities for extracting information from {@link DocElement}.
 */
public class DocElementUtil {

  private DocElementUtil() {
  }

  /**
   * Given an object of a class that has the annotation {@link DocElement}, returns the yaml name of
   * the type.
   */
  public static String getYamlName(Object obj) {
    Preconditions.checkNotNull(obj, "Cannot extract annotations from a null object");
    DocElement annotation = obj.getClass().getAnnotation(DocElement.class);
    Verify.verifyNotNull(annotation, "Cannot find DocElement in class %s", obj.getClass());
    return annotation.yamlName();
  }
}
