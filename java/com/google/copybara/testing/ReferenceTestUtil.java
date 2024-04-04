/*
 * Copyright (C) 2024 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.copybara.testing;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;
import com.google.copybara.doc.annotations.DocSignaturePrefix;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;

/** Utility for verifying the generated reference */
public class ReferenceTestUtil {

  private final String reference;

  public ReferenceTestUtil(String reference) {
    this.reference = reference;
  }

  /** Returns a list of classes with @StarlarkBuiltin that are not in the reference. */
  public ImmutableList<Class<?>> getMissingTopLevelEntries() throws IOException {
    ImmutableList.Builder<Class<?>> results = ImmutableList.builder();
    for (Class<?> clazz : getStarlarkClasses()) {
      Optional<String> name = getStarlarkClassName(clazz);
      if (name.isEmpty()) {
        continue;
      }
      String lookup = String.format("[%s]", name.get());
      if (!reference.contains(lookup)) {
        results.add(clazz);
      }
    }
    return results.build();
  }

  /** Returns a list of methods with @StarlarkMethod that are not in the reference. */
  public ImmutableList<StarlarkSymbol> getMissingMethods() throws IOException {
    ImmutableSet<Class<?>> classes = getStarlarkClasses();
    Map<Method, StarlarkSymbol> unDocumented = new HashMap<>();
    Set<Method> documented = new HashSet<>();

    for (Entry<Class<?>, Method> method : getStarlarkMethods().entries()) {
      String lookup =
          String.format(
              "[%s%s]",
              getStarlarkMethodPrefix(classes, method.getKey(), method.getValue()),
              getMethodName(method.getValue(), method.getKey()));
      // We assume that if a Method is included for any object, it's documented. This might be
      // false for complex inheritance scenarios.
      if (!reference.contains(lookup) && !documented.contains(method.getValue())) {
        unDocumented.put(method.getValue(), StarlarkSymbol.create(lookup, method.getKey()));
      } else {
        documented.add(method.getValue());
        unDocumented.remove(method.getValue());
      }
    }
    return ImmutableList.copyOf(unDocumented.values());
  }

  private static String getMethodName(Method method, Class<?> clazz) {
    StarlarkMethod annotation = method.getAnnotation(StarlarkMethod.class);
    if (annotation.selfCall()) {
      return getStarlarkClassName(clazz).orElse(annotation.name());
    }
    return annotation.name();
  }

  private ImmutableSet<Class<?>> getStarlarkClasses() throws IOException {
    ImmutableSet.Builder<Class<?>> expected = ImmutableSet.builder();
    for (ClassInfo info : ClassPath.from(this.getClass().getClassLoader()).getAllClasses()) {
      if (!info.getPackageName().contains("copybara")) {
        continue;
      }
      Class<?> clazz = info.load();
      if (clazz.isAnnotationPresent(StarlarkBuiltin.class)
          && clazz.getAnnotation(StarlarkBuiltin.class).documented()) {
        expected.add(clazz);
      }
    }
    return expected.build();
  }

  private String getStarlarkMethodPrefix(
      ImmutableSet<Class<?>> classes, Class<?> clazz, Method method) {
    if (!classes.contains(clazz)
        || (method.isAnnotationPresent(StarlarkMethod.class)
            && method.getAnnotation(StarlarkMethod.class).selfCall())) {
      return "";
    }
    if (clazz.isAnnotationPresent(DocSignaturePrefix.class)) {
      return clazz.getAnnotation(DocSignaturePrefix.class).value() + ".";
    }
    return getStarlarkClassName(clazz).get() + ".";
  }

  public static Optional<String> getStarlarkClassName(Class<?> clazz) {
    if (!clazz.isAnnotationPresent(StarlarkBuiltin.class)) {
      return Optional.empty();
    }
    return Optional.of(clazz.getAnnotation(StarlarkBuiltin.class).name());
  }

  private ImmutableListMultimap<Class<?>, Method> getStarlarkMethods() throws IOException {
    ClassPath classPath = ClassPath.from(this.getClass().getClassLoader());
    ImmutableListMultimap.Builder<Class<?>, Method> expected = ImmutableListMultimap.builder();
    for (ClassInfo info : classPath.getAllClasses()) {
      if (!info.getPackageName().contains("copybara")) {
        continue;
      }
      Class<?> clazz = info.load();
      if (clazz.isAnnotationPresent(StarlarkBuiltin.class)
          && !clazz.getAnnotation(StarlarkBuiltin.class).documented()) {
        continue;
      }
      for (Method m : clazz.getMethods()) {
        if (!isDocumentedMethod(m)) {
          continue;
        }
        expected.put(clazz, m);
      }
    }
    return expected.build();
  }

  private static boolean isDocumentedMethod(Method method) {
    return method.isAnnotationPresent(StarlarkMethod.class)
        && method.getAnnotation(StarlarkMethod.class).documented()
        && !method.getAnnotation(StarlarkMethod.class).structField();
  }

  /** Valuetype for Starlarkethods */
  @AutoValue
  public abstract static class StarlarkSymbol {
    public abstract String name();

    public abstract Class<?> definingClass();

    public static StarlarkSymbol create(String name, Class<?> definingClass) {
      return new AutoValue_ReferenceTestUtil_StarlarkSymbol(name, definingClass);
    }
  }
}
