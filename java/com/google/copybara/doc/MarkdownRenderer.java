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

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;

import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.copybara.doc.DocBase.DocExample;
import com.google.copybara.doc.DocBase.DocField;
import com.google.copybara.doc.DocBase.DocFlag;
import com.google.copybara.doc.DocBase.DocFunction;
import com.google.copybara.doc.DocBase.DocModule;
import com.google.copybara.doc.DocBase.DocParam;
import com.google.copybara.doc.annotations.Example;
import java.util.Collection;

final class MarkdownRenderer {

  private static final int MODULE_HEADING_LEVEL = 2;

  public CharSequence render(Iterable<DocModule> modules) {
    StringBuilder sb = new StringBuilder();
    sb.append(tableOfContents(modules));

    for (DocModule module : modules) {
      sb.append("\n");
      sb.append(module(module, MODULE_HEADING_LEVEL));
    }
    return sb;
  }

  private CharSequence tableOfContents(Iterable<DocModule> modules) {
    StringBuilder sb = new StringBuilder();
    sb.append("## Table of Contents\n\n\n");
    for (DocModule module : modules) {
      sb.append("  - ");
      sb.append(linkify(module.name));
      sb.append("\n");
      for (DocFunction f : module.functions) {
        sb.append("    - ");
        sb.append(linkify(f.name));
        sb.append("\n");
      }
    }
    sb.append("\n");
    return sb;
  }

  private CharSequence module(DocModule module, int level) {
    StringBuilder sb = new StringBuilder();
    sb.append(title(level, module.name));
    sb.append(module.description).append("\n\n");

    if (!module.fields.isEmpty()) {
      sb.append(title(level + 2, "Fields:"));
      sb.append(tableHeader("Name", "Description"));
      for (DocField field : module.fields) {
        sb.append(
            tableRow(
                field.name, String.format("`%s`<br><p>%s</p>", field.type, field.description)));
      }
      sb.append("\n");
    }

    sb.append(flags(module.flags));

    for (DocFunction func : module.functions) {
      sb.append("<a id=\"").append(func.name).append("\" aria-hidden=\"true\"></a>");
      sb.append(title(level + 1, func.name));
      sb.append(func.description);
      sb.append("\n\n");
      sb.append("`");
      if (func.returnType != null) {
        sb.append(func.returnType).append(" ");
      }
      sb.append(func.name).append("(");
      Joiner.on(", ")
          .appendTo(
              sb,
              Lists.transform(
                  func.params, p -> p.name + (p.defaultValue == null ? "" : "=" + p.defaultValue)));
      sb.append(")`\n\n");

      if (!func.params.isEmpty()) {
        sb.append(title(level + 2, "Parameters:"));
        sb.append(tableHeader("Parameter", "Description"));
        for (DocParam param : func.params) {
          sb.append(
              tableRow(
                  param.name,
                  String.format(
                      "`%s`<br><p>%s</p>",
                      Joiner.on("` or `").join(param.allowedTypes), param.description)));
        }
        sb.append("\n");
      }
      if (!func.examples.isEmpty()) {
        sb.append(title(level + 2, func.examples.size() == 1 ? "Example:" : "Examples:"));
        for (DocExample example : func.examples) {
          sb.append(example(level + 3, example.example));
        }
        sb.append("\n");
      }
      sb.append(flags(func.flags));
    }
    return sb;
  }

  private String title(int level, String name) {
    return "\n" + Strings.repeat("#", level) + ' ' + name + "\n\n";
  }

  private String linkify(String name) {
    return "[" + name + "](#" + Ascii.toLowerCase(name).replace(".", "") + ")";
  }

  private String example(int level, Example example) {
    StringBuilder sb = new StringBuilder();
    sb.append(title(level, example.title() + ":"));
    sb.append(example.before()).append("\n\n");
    sb.append("```python\n").append(example.code()).append("\n```\n\n");
    if (!example.after().isEmpty()) {
      sb.append(example.after()).append("\n\n");
    }
    return sb.toString();
  }

  private static String flags(Collection<DocFlag> flags) {
    StringBuilder sb = new StringBuilder();
    if (!flags.isEmpty()) {
      sb.append("\n\n**Command line flags:**\n\n");
      sb.append(tableHeader("Name", "Type", "Description"));
      for (DocFlag field : flags) {
        sb.append(
            tableRow(nowrap(field.name), String.format("*%s*", field.type), field.description));
      }
      sb.append("\n");
    }
    return sb.toString();
  }

  /** Don't wrap this text. Also use '`' to show it as code. */
  private static String nowrap(String text) {
    return String.format("<span style=\"white-space: nowrap;\">`%s`</span>", text);
  }

  private static String tableHeader(String... fields) {
    return tableRow(fields)
        + tableRow(stream(fields).map(e -> Strings.repeat("-", e.length())).toArray(String[]::new));
  }

  private static String tableRow(String... fields) {
    return stream(fields).map(s -> s.replace("\n", "<br>")).collect(joining(" | ")) + "\n";
  }
}
