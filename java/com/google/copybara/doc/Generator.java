/*
 * Copyright (C) 2020 Google Inc.
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

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.function.Function.identity;

import com.beust.jcommander.Parameter;
import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.html.HtmlEscapers;
import com.google.copybara.doc.annotations.DocDefault;
import com.google.copybara.doc.annotations.DocSignaturePrefix;
import com.google.copybara.doc.annotations.Example;
import com.google.copybara.doc.annotations.Library;
import com.google.copybara.doc.annotations.UsesFlags;
import com.google.copybara.jcommander.DurationConverter;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.NoneType;
import net.starlark.java.eval.Starlark;

/**
 * Given a file with list of classes, an output file, and an optional template file, generates
 * a Markdown document with Copybara reference guide.
 */
public class Generator {

  private static final String TEMPLATE_REPLACEMENT = "<!-- Generated reference here -->";

  private Generator() {
  }

  public static void main(String[] args) throws IOException {
    List<DocModule> modules = new Generator().generate(Paths.get(args[0]));
    writeMarkdown(
        modules,
        args.length > 1
            ? new String(Files.readAllBytes(Paths.get(args[1])), StandardCharsets.UTF_8)
            : TEMPLATE_REPLACEMENT);
  }

  private static void mdTitle(StringBuilder sb, int level, String name) {
    sb.append("\n").append(Strings.repeat("#", level)).append(' ').append(name).append("\n\n");
  }

  private ImmutableList<DocModule> generate(Path classListFile) throws IOException {
    List<String> classes = Files.readAllLines(classListFile, StandardCharsets.UTF_8);

    List<DocModule> modules = new ArrayList<>();
    DocModule docModule = new DocModule("Globals", "Global functions available in Copybara");
    modules.add(docModule);
    for (String clsName : classes) {
      try {
        Class<?> cls = Generator.class.getClassLoader().loadClass(clsName);

        getAnnotation(cls, Library.class)
            .ifPresent(library -> docModule.functions.addAll(processFunctions(cls, null)));

        getAnnotation(cls, StarlarkBuiltin.class)
            .ifPresent(library -> {
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

  private static void writeMarkdown(Iterable<DocModule> modules, String template) {
    StringBuilder sb = new StringBuilder("## Table of Contents\n\n\n");
    for (DocModule module : modules) {
      sb.append("  - [");
      sb.append(module.name);
      sb.append("](#");
      sb.append(Ascii.toLowerCase(module.name).replace(".", ""));
      sb.append(")\n");
      for (DocFunction f : module.functions) {
        sb.append("    - [");
        sb.append(f.name);
        sb.append("](#");
        sb.append(Ascii.toLowerCase(f.name).replace(".", ""));
        sb.append(")\n");
      }
    }
    sb.append("\n");
    for (DocModule module : modules) {
      sb.append("\n");
      sb.append(module.toMarkdown(2));
    }
    System.out.println(
        template.replace(TEMPLATE_REPLACEMENT, TEMPLATE_REPLACEMENT + "\n" + sb.toString()));
  }

  private ImmutableList<DocField> processFields(Class<?> cls) {
    return Starlark.getMethodAnnotations(cls).entrySet().stream()
        .filter(e -> e.getValue().structField())
        .map(e -> processStarlarkMethod(cls, e.getKey(), e.getValue(), null))
        .map(m -> new DocField(m.name, m.description))
        .collect(ImmutableList.toImmutableList());
  }

  private ImmutableList<DocFunction> processFunctions(Class<?> cls, String prefix) {
    return Starlark.getMethodAnnotations(cls).entrySet().stream()
        .filter(e -> !e.getValue().structField())
        .map(e -> processStarlarkMethod(cls, e.getKey(), e.getValue(), prefix))
        .collect(ImmutableList.toImmutableList());
  }

  private DocFunction processStarlarkMethod(Class<?> cls, Method method,
      StarlarkMethod annotation, @Nullable String prefix) {

    Type[] genericParameterTypes = method.getGenericParameterTypes();

    Param[] starlarkParams = annotation.parameters();

    if (genericParameterTypes.length < starlarkParams.length) {
      throw new IllegalStateException(String.format("Missing java parameters for: %s\n"
          + "%s\n"
          + "%s", method, Arrays.toString(genericParameterTypes), Arrays.toString(starlarkParams)));
    }
    ImmutableList.Builder<DocParam> params = ImmutableList.builder();

    Map<String, DocDefault> docDefaultsMap = Arrays
        .stream(method.getAnnotationsByType(DocDefault.class))
        .collect(Collectors.toMap(DocDefault::field, identity(), (f, v) -> v));

    for (int i = 0; i < starlarkParams.length; i++) {
      Type parameterType = genericParameterTypes[i];
      Param starlarkParam = starlarkParams[i];

      // Compute the list of names of allowed types (e.g. string or bool or NoneType).
      List<String> allowedTypeNames = new ArrayList<String>();
      if (starlarkParam.allowedTypes().length > 0) {
        for (ParamType param : starlarkParam.allowedTypes()) {
          allowedTypeNames.add(skylarkTypeName(param.type()) + (
              param.generic1() != Object.class ? " of " + skylarkTypeName(param.generic1()) : ""));
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
              fieldInfo != null
                  ? fieldInfo.value()
                  : Strings.isNullOrEmpty(starlarkParam.defaultValue())
                      ? null
                      : starlarkParam.defaultValue(),
              allowedTypeNames,
              starlarkParam.doc()));
    }

    String returnType = method.getGenericReturnType().equals(NoneType.class)
        || method.getGenericReturnType().equals(void.class)
        ? null
        : skylarkTypeName(method.getGenericReturnType());

    return new DocFunction(
        prefix != null ? prefix + "." + annotation.name() : annotation.name(),
        annotation.doc(),
        returnType,
        params.build(),
        generateFlagsInfo(method),
        Arrays.stream(method.getAnnotationsByType(Example.class))
            .map(DocExample::new)
            .collect(ImmutableList.toImmutableList()));
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
                      description += (description.endsWith(".") ? " " : ". ")
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
            : String.format("dict[%s, %s]",
                skylarkTypeName(first),
                skylarkTypeName(second));
      }

      if (Iterable.class.isAssignableFrom((Class<?>) pType.getRawType())) {
        Type first = pType.getActualTypeArguments()[0];
        return isObject(first) ? "sequence"
            : String.format("sequence of %s", skylarkTypeName(first));
      }

      return Starlark.classType((Class<?>) pType.getRawType());
    }

    if (type instanceof Class<?>) {
      return Starlark.classType((Class<?>) type);
    }

    throw new RuntimeException("Unsupported type " + type + " " + type.getClass());
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

  private void tableHeader(StringBuilder sb, String... fields) {
    tableRow(sb, fields);
    tableRow(sb, Stream.of(fields)
        .map(e -> Strings.repeat("-", e.length()))
        .toArray(String[]::new));
  }

  private void tableRow(StringBuilder sb, String... fields) {
    sb.append(Arrays.stream(fields).map(s -> s.replace("\n", "<br>"))
        .collect(Collectors.joining(" | ")))
        .append("\n");
  }

  private abstract static class DocBase implements Comparable<DocBase> {

    protected final String name;
    protected final String description;

    DocBase(String name, String description) {
      this.name = checkNotNull(name);
      this.description = checkNotNull(description);
    }

    @Override
    public int compareTo(DocBase o) {
      return name.compareTo(o.name);
    }

    public String getName() {
      return name;
    }
  }

  private static final class DocField extends DocBase {

    DocField(String name, String description) {
      super(name, description);
    }
  }

  private final class DocFunction extends DocBase {

    private final TreeSet<DocFlag> flags = new TreeSet<>();
    @Nullable
    private final String returnType;
    private final ImmutableList<DocParam> params;
    private final ImmutableList<DocExample> examples;

    DocFunction(String name, String description, @Nullable String returnType,
        Iterable<DocParam> params, Iterable<DocFlag> flags, Iterable<DocExample> examples) {
      super(name, description);
      this.returnType = returnType;
      this.params = ImmutableList.copyOf(params);
      this.examples = ImmutableList.copyOf(examples);
      Iterables.addAll(this.flags, flags);
    }
  }

  private final class DocModule extends DocBase {

    private final TreeSet<DocField> fields = new TreeSet<>();
    private final TreeSet<DocFunction> functions = new TreeSet<>();
    private final TreeSet<DocFlag> flags = new TreeSet<>();

    DocModule(String name, String description) {
      super(name, description);
    }

    CharSequence toMarkdown(int level) {
      StringBuilder sb = new StringBuilder();
      mdTitle(sb, level, name);
      sb.append(description).append("\n\n");

      if (!fields.isEmpty()) {
        // TODO(malcon): Skip showing in ToC for now by showing it as a more deep element.
        mdTitle(sb, level + 2, "Fields:");
        tableHeader(sb, "Name", "Description");
        for (DocField field : fields) {
          tableRow(sb, field.name, field.description);
        }
        sb.append("\n");
      }

      printFlags(sb, flags);

      for (DocFunction func : functions) {
        sb.append("<a id=\"").append(func.name).append("\" aria-hidden=\"true\"></a>");
        mdTitle(sb, level + 1, func.name);
        sb.append(func.description);
        sb.append("\n\n");
        sb.append("`");
        if (func.returnType != null) {
          sb.append(func.returnType).append(" ");
        }
        sb.append(func.name).append("(");
        Joiner.on(", ").appendTo(sb, Lists.transform(func.params,
            p -> p.name + (p.defaultValue == null ? "" : "=" + p.defaultValue)));
        sb.append(")`\n\n");

        if (!Iterables.isEmpty(func.params)) {
          mdTitle(sb, level + 2, "Parameters:");
          tableHeader(sb, "Parameter", "Description");
          for (DocParam param : func.params) {
            tableRow(
                sb,
                param.name,
                String.format(
                    "`%s`<br><p>%s</p>",
                    Joiner.on("` or `").join(param.allowedTypes), param.description));
          }
          sb.append("\n");
        }
        if (!func.examples.isEmpty()) {
          mdTitle(sb, level + 2, func.examples.size() == 1 ? "Example:" : "Examples:");
          for (DocExample example : func.examples) {
            printExample(sb, level + 3, example.example);
          }
          sb.append("\n");
        }
        printFlags(sb, func.flags);
      }
      return sb;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("name", name).toString();
    }

    private void printExample(StringBuilder sb, int level, Example example) {
      mdTitle(sb, level, example.title() + ":");
      sb.append(example.before()).append("\n\n");
      sb.append("```python\n").append(example.code()).append("\n```\n\n");
      if (!example.after().equals("")) {
        sb.append(example.after()).append("\n\n");
      }
    }


    private void printFlags(StringBuilder sb, Collection<DocFlag> flags) {
      if (!flags.isEmpty()) {
        sb.append("\n\n**Command line flags:**\n\n");
        tableHeader(sb, "Name", "Type", "Description");
        for (DocFlag field : flags) {
          tableRow(sb, nowrap(field.name), String.format("*%s*", field.type), field.description);
        }
        sb.append("\n");
      }
    }

    /**
     * Don't wrap this text. Also use '`' to show it as code.
     */
    private String nowrap(String text) {
      return String.format("<span style=\"white-space: nowrap;\">`%s`</span>", text);
    }
  }

  private final class DocParam {

    private final String name;
    @Nullable
    private final String defaultValue;
    private final List<String> allowedTypes;
    private final String description;

    DocParam(
        String name, @Nullable String defaultValue, List<String> allowedTypes, String description) {
      this.name = name;
      this.defaultValue = defaultValue;
      this.allowedTypes = allowedTypes;
      this.description = description;
    }
  }

  private final class DocFlag extends DocBase {

    public final String type;

    DocFlag(String name, String type, String description) {
      super(name, description);
      this.type = type;
    }
  }

  private final class DocExample {

    private final Example example;

    DocExample(Example example) {
      this.example = example;
    }
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
