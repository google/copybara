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

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.copybara.doc.annotations.Example;
import com.google.copybara.doc.annotations.Library;

import net.starlark.java.annot.StarlarkMethod;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

public class Generator {

  private Generator() {
  }

  public static void main(String[] args) throws IOException {
    new Generator().generate(Paths.get(args[0]), Paths.get(args[1]));
  }

  private static void mdTitle(StringBuilder sb, int level, String name) {
    sb.append("\n").append(Strings.repeat("#", level)).append(' ').append(name).append("\n\n");
  }

  private void generate(Path classListFile, Path outputFile)
      throws IOException {
    List<String> classes = Files.readAllLines(classListFile, StandardCharsets.UTF_8);

    List<DocModule> modules = new ArrayList<>();
    DocModule docModule = new DocModule("Globals", "Global functions available in Copybara");
    modules.add(docModule);

    for (String clsName : classes) {
      try {
        Class<?> cls = Generator.class.getClassLoader().loadClass(clsName);

        getAnnotation(cls, Library.class)
            .ifPresent(library -> docModule.functions.addAll(processLibrary(cls)));

      } catch (ClassNotFoundException e) {
        throw new IllegalStateException("Cannot generate documentation for " + clsName, e);
      }
    }

    StringBuilder sb = new StringBuilder("## Table of Contents\n\n");
    for (DocModule module : modules) {
      sb.append("  - [");
      sb.append(module.name);
      sb.append("](#");
      sb.append(module.name.toLowerCase());
      sb.append(")\n");
    }
    sb.append("\n");
    for (DocModule module : modules) {
      sb.append(module.toMarkdown(2));
    }
    Files.write(outputFile, sb.toString().getBytes(StandardCharsets.UTF_8));
  }

  private ImmutableList<DocFunction> processLibrary(Class<?> cls) {
    ImmutableList.Builder<DocFunction> result = ImmutableList.builder();
    for (Method method : cls.getMethods()) {
      getAnnotation(method, StarlarkMethod.class)
          .ifPresent(sMethod -> result.add(processStarlarkMethod(cls, method, sMethod)));
    }
    return result.build();
  }

  private DocFunction processStarlarkMethod(Class<?> cls, Method method, StarlarkMethod sMethod) {
    // TODO(malcon): Implement starlark method.
    return new DocFunction(sMethod.name(), sMethod.doc(), "TBD", ImmutableList.of(),
        ImmutableList.of(), ImmutableList.of());
  }

  @SuppressWarnings("unchecked")
  private <T extends Annotation> Optional<T> getAnnotation(AnnotatedElement cls, Class<T> annCls) {
    for (Annotation ann : cls.getAnnotations()) {
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

}
