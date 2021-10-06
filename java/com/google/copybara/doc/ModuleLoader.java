/*
 * Copyright (C) 2021 Google Inc.
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

package com.google.copybara.doc;

import static com.google.common.base.Strings.emptyToNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Arrays.stream;
import static java.util.function.Function.identity;

import com.beust.jcommander.Parameter;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.html.HtmlEscapers;
import com.google.copybara.doc.DocBase.DocExample;
import com.google.copybara.doc.DocBase.DocField;
import com.google.copybara.doc.DocBase.DocFlag;
import com.google.copybara.doc.DocBase.DocFunction;
import com.google.copybara.doc.DocBase.DocModule;
import com.google.copybara.doc.DocBase.DocParam;
import com.google.copybara.doc.annotations.DocDefault;
import com.google.copybara.doc.annotations.DocSignaturePrefix;
import com.google.copybara.doc.annotations.Example;
import com.google.copybara.doc.annotations.Library;
import com.google.copybara.doc.annotations.UsesFlags;
import com.google.copybara.jcommander.DurationConverter;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.NoneType;
import net.starlark.java.eval.Starlark;

final class ModuleLoader {

  public ImmutableList<DocModule> load(List<String> classes) {
    List<DocModule> modules = new ArrayList<>();
    DocModule docModule = new DocModule("Globals", "Global functions available in Copybara");
    modules.add(docModule);
    for (String clsName : classes) {
      try {
        Class<?> cls = Generator.class.getClassLoader().loadClass(clsName);

        getAnnotation(cls, Library.class)
            .ifPresent(library -> docModule.functions.addAll(processFunctions(cls, null)));

        getAnnotation(cls, StarlarkBuiltin.class)
            .ifPresent(
                library -> {
                  if (!library.documented()) {
                    return;
                  }
                  DocSignaturePrefix prefixAnn = cls.getAnnotation(DocSignaturePrefix.class);
                  String prefix = prefixAnn != null ? prefixAnn.value() : library.name();

                  DocModule mod = new DocModule(library.name(), library.doc());
                  mod.functions.addAll(processFunctions(cls, prefix));
                  mod.fields.addAll(processFields(cls));
                  mod.flags.addAll(generateFlagsInfo(cls));
                  modules.add(mod);
                });

      } catch (ClassNotFoundException e) {
        throw new IllegalStateException("Cannot generate documentation for " + clsName, e);
      }
    }

    return deduplicateAndSort(modules);
  }

  private ImmutableList<DocField> processFields(Class<?> cls) {
    return Starlark.getMethodAnnotations(cls).entrySet().stream()
        .filter(e -> e.getValue().structField())
        .map(e -> processStarlarkMethod(e.getKey(), e.getValue(), null))
        .map(m -> new DocField(m.name, m.description, m.returnType))
        .collect(toImmutableList());
  }

  private ImmutableList<DocFunction> processFunctions(Class<?> cls, String prefix) {
    return Starlark.getMethodAnnotations(cls).entrySet().stream()
        .filter(e -> !e.getValue().structField())
        .map(e -> processStarlarkMethod(e.getKey(), e.getValue(), prefix))
        .collect(toImmutableList());
  }

  private DocFunction processStarlarkMethod(
      Method method, StarlarkMethod annotation, @Nullable String prefix) {

    Type[] genericParameterTypes = method.getGenericParameterTypes();

    Param[] starlarkParams = annotation.parameters();

    if (genericParameterTypes.length < starlarkParams.length) {
      throw new IllegalStateException(
          String.format(
              "Missing java parameters for: %s\n" + "%s\n" + "%s",
              method, Arrays.toString(genericParameterTypes), Arrays.toString(starlarkParams)));
    }
    ImmutableList.Builder<DocParam> params = ImmutableList.builder();

    Map<String, DocDefault> docDefaultsMap =
        stream(method.getAnnotationsByType(DocDefault.class))
            .collect(Collectors.toMap(DocDefault::field, identity(), (f, v) -> v));

    for (int i = 0; i < starlarkParams.length; i++) {
      Type parameterType = genericParameterTypes[i];
      Param starlarkParam = starlarkParams[i];

      // Compute the list of names of allowed types (e.g. string or bool or NoneType).
      List<String> allowedTypeNames = new ArrayList<>();
      if (starlarkParam.allowedTypes().length > 0) {
        for (ParamType param : starlarkParam.allowedTypes()) {
          allowedTypeNames.add(
              skylarkTypeName(param.type())
                  + (param.generic1() != Object.class
                      ? " of " + skylarkTypeName(param.generic1())
                      : ""));
        }
      } else {
        // Otherwise use the type of the parameter variable itself.
        allowedTypeNames.add(skylarkTypeName(parameterType));
      }
      DocDefault fieldInfo = docDefaultsMap.get(starlarkParam.name());
      if (fieldInfo != null && fieldInfo.allowedTypes().length > 0) {
        allowedTypeNames = Arrays.asList(fieldInfo.allowedTypes());
      }
      params.add(
          new DocParam(
              starlarkParam.name(),
              fieldInfo != null ? fieldInfo.value() : emptyToNull(starlarkParam.defaultValue()),
              allowedTypeNames,
              starlarkParam.doc()));
    }

    String returnType =
        method.getGenericReturnType().equals(NoneType.class)
                || method.getGenericReturnType().equals(void.class)
            ? null
            : skylarkTypeName(method.getGenericReturnType());

    return new DocFunction(
        prefix != null ? prefix + "." + annotation.name() : annotation.name(),
        annotation.doc(),
        returnType,
        params.build(),
        generateFlagsInfo(method),
        stream(method.getAnnotationsByType(Example.class))
            .map(DocExample::new)
            .collect(toImmutableList()));
  }

  private Collection<DocFlag> generateFlagsInfo(AnnotatedElement el) {

    List<DocFlag> result = new ArrayList<>();
    getAnnotation(el, UsesFlags.class)
        .ifPresent(
            cls -> {
              for (Class<?> c : cls.value()) {
                for (Field f : c.getDeclaredFields()) {
                  for (Parameter p : f.getAnnotationsByType(Parameter.class)) {
                    if (p.hidden()) {
                      continue;
                    }
                    String description = p.description();
                    if (DurationConverter.class.isAssignableFrom(p.converter())) {
                      description +=
                          (description.endsWith(".") ? " " : ". ")
                              + " Example values: 30s, 20m, 1h, etc.";
                    }
                    result.add(
                        new DocFlag(
                            Joiner.on(", ").join(p.names()),
                            simplerJavaTypes(f.getType()),
                            description));
                  }
                }
              }
            });

    return result;
  }

  private String simplerJavaTypes(Class<?> s) {
    if (s.isEnum()) {
      return "`" + Joiner.on("`<br>or `").join(s.getEnumConstants()) + "`";
    }
    Matcher m = Pattern.compile("(?:[A-z.]*\\.)*([A-z]+)").matcher(s.getName());
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      String replacement = deCapitalize(m.group(1));
      m.appendReplacement(sb, replacement);
    }
    m.appendTail(sb);

    return HtmlEscapers.htmlEscaper().escape(sb.toString());
  }

  private String deCapitalize(String substring) {
    return Character.toLowerCase(substring.charAt(0)) + substring.substring(1);
  }

  // TODO(malcon): Simplify this method when Starlark provides better type introspection.
  private String skylarkTypeName(Type type) {
    if (type instanceof WildcardType) {
      WildcardType wild = (WildcardType) type;
      // Assume "? extends Foo" and ignore "? super Bar" for now.
      type = wild.getUpperBounds()[0];
    }

    if (type instanceof ParameterizedType) {
      ParameterizedType pType = (ParameterizedType) type;
      if (Map.class.isAssignableFrom((Class<?>) pType.getRawType())) {
        Type first = pType.getActualTypeArguments()[0];
        Type second = pType.getActualTypeArguments()[1];
        return isObject(first) || isObject(second)
            ? "dict"
            : String.format("dict[%s, %s]", skylarkTypeName(first), skylarkTypeName(second));
      }

      if (Iterable.class.isAssignableFrom((Class<?>) pType.getRawType())) {
        Type first = pType.getActualTypeArguments()[0];
        return isObject(first)
            ? "sequence"
            : String.format("sequence of %s", skylarkTypeName(first));
      }

      return Starlark.classType((Class<?>) pType.getRawType());
    }

    if (type instanceof Class<?>) {
      return Starlark.classType((Class<?>) type);
    }

    throw new IllegalArgumentException("Unsupported type " + type + " " + type.getClass());
  }

  private boolean isObject(Type type) {
    if (type == Object.class) {
      return true;
    }
    if (type instanceof WildcardType) {
      WildcardType wildcard = (WildcardType) type;
      return wildcard.getUpperBounds()[0].equals(Object.class);
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  private <T extends Annotation> Optional<T> getAnnotation(AnnotatedElement el, Class<T> annCls) {
    for (Annotation ann : el.getAnnotations()) {
      if (ann.annotationType().equals(annCls)) {
        return Optional.of((T) ann);
      }
    }
    return Optional.empty();
  }

  private ImmutableList<DocModule> deduplicateAndSort(Collection<DocModule> modules) {
    SortedMap<String, DocModule> asMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    for (DocModule module : modules) {
      DocModule existing = asMap.get(module.name);
      if (existing == null
          || existing.functions.size() < module.functions.size()
          || existing.fields.size() < module.fields.size()
          || existing.flags.size() < module.flags.size()) {
        asMap.put(module.name, module);
      }
    }

    return ImmutableList.copyOf(asMap.values());
  }
}
