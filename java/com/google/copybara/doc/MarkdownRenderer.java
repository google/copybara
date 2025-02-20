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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.copybara.doc.DocBase.DocExample;
import com.google.copybara.doc.DocBase.DocField;
import com.google.copybara.doc.DocBase.DocFlag;
import com.google.copybara.doc.DocBase.DocFunction;
import com.google.copybara.doc.DocBase.DocModule;
import com.google.copybara.doc.DocBase.DocParam;
import com.google.copybara.doc.annotations.Example;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

final class MarkdownRenderer {

  private static final int MODULE_HEADING_LEVEL = 2;

  private final Set<String> headings = new HashSet<>();

  private final Map<String, ImmutableSet<String>> returnedBy =
      new HashMap<String, ImmutableSet<String>>();

  private void addToMapValueSet(Map<String, ImmutableSet<String>> map, String key, String value) {
    ImmutableSet.Builder<String> builder = ImmutableSet.builder();
    builder.addAll(map.getOrDefault(key, ImmutableSet.of()));
    builder.add(value);
    map.put(key, builder.build());
  }

  private final Map<String, ImmutableSet<String>> consumedBy =
      new HashMap<String, ImmutableSet<String>>();

  public CharSequence render(Iterable<DocModule> modules, boolean includeFlagAggregate) {
    ImmutableList<DocModule> modulesToRender =
        new ImmutableList.Builder<DocModule>()
            .addAll(Iterables.filter(modules, DocModule::isDocumented))
            .addAll(
                includeFlagAggregate ? ImmutableList.of(renderFlags(modules)) : ImmutableList.of())
            .build();

    populateUsageMaps(modulesToRender);

    StringBuilder sb = new StringBuilder();
    sb.append(tableOfContents(modulesToRender));

    for (DocModule module : modulesToRender) {
      sb.append("\n");
      sb.append(module(module, MODULE_HEADING_LEVEL));
    }
    return sb;
  }

  private void populateUsageMaps(Iterable<DocModule> modules) {
    for (DocModule module : Iterables.filter(modules, DocModule::isDocumented)) {
      for (DocFunction f : Iterables.filter(module.functions, DocFunction::isDocumented)) {
        if (f.returnType != null) {
          // add f to the set of functions that return f.returnType
          addToMapValueSet(returnedBy, f.returnType, f.name);

          // capture types within container types as well
          if (f.returnType.startsWith("sequence of ")) {
            addToMapValueSet(returnedBy, getSequenceElementType(f.returnType), f.name);
          }
          if (f.returnType.startsWith("dict[")) {
            addToMapValueSet(returnedBy, getDictKeyType(f.returnType), f.name);
            addToMapValueSet(returnedBy, getDictValueType(f.returnType), f.name);
          }
        }

        for (DocParam param : Iterables.filter(f.params, DocParam::isDocumented)) {
          // for each param, for each type accepted by that param,
          // add f to the set of functions that consume the type of that param
          for (String type : param.allowedTypes) {
            addToMapValueSet(consumedBy, type, f.name);

            if (type.startsWith("sequence of ")) {
              addToMapValueSet(consumedBy, getSequenceElementType(type), f.name);
            }
            if (type.startsWith("dict[")) {
              addToMapValueSet(consumedBy, getDictKeyType(type), f.name);
              addToMapValueSet(consumedBy, getDictValueType(type), f.name);
            }
          }
        }
      }
    }
  }

  private DocModule renderFlags(Iterable<DocModule> modules) {
    TreeSet<DocFlag> flagSet = new TreeSet<>();
    Iterables.filter(modules, DocModule::isDocumented)
        .forEach(module -> flagSet.addAll(module.flags));
    DocModule flagModule =
        new DocModule("copybara_flags", "All flag options available to the Copybara CLI.", true);
    flagModule.flags.addAll(flagSet);
    return flagModule;
  }

  private CharSequence tableOfContents(Iterable<DocModule> modules) {
    StringBuilder sb = new StringBuilder();
    sb.append("## Table of Contents\n\n\n");
    for (DocModule module : modules) {
      headings.add(module.name);
      sb.append("  - ");
      sb.append(linkify(module.name));
      sb.append("\n");
      for (DocFunction f : Iterables.filter(module.functions, DocFunction::isDocumented)) {
        headings.add(f.name);
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
      sb.append(htmlTitle(level + 2, "Fields:", "fields." + module.name));
      sb.append(tableHeader("Name", "Description"));
      for (DocField field : module.fields) {
        sb.append(
            tableRow(
                field.name,
                String.format("%s<br><p>%s</p>", typeName(field.type), field.description)));
      }
      sb.append("\n");
    }

    sb.append(flags(module.flags));

    ImmutableSet<String> moduleReturnedBy = returnedBy.getOrDefault(module.name, ImmutableSet.of());
    if (!moduleReturnedBy.isEmpty()) {
      sb.append(htmlTitle(level + 2, "Returned By:", "returned_by." + module.name));
      sb.append("<ul>");
      for (String funcName : moduleReturnedBy) {
        sb.append(String.format("<li><a href=\"#%s\">%s</a></li>", funcName, funcName));
      }
      sb.append("</ul>");
    }
    ImmutableSet<String> moduleConsumedBy = consumedBy.getOrDefault(module.name, ImmutableSet.of());
    if (!moduleConsumedBy.isEmpty()) {
      sb.append(htmlTitle(level + 2, "Consumed By:", "consumed_by." + module.name));
      sb.append("<ul>");
      for (String funcName : moduleConsumedBy) {
        sb.append(String.format("<li><a href=\"#%s\">%s</a></li>", funcName, funcName));
      }
      sb.append("</ul>");
    }

    if (!moduleReturnedBy.isEmpty() || !moduleConsumedBy.isEmpty()) {
      sb.append("\n\n");
    }

    for (DocFunction func : Iterables.filter(module.functions, DocFunction::isDocumented)) {
      sb.append("<a id=\"").append(func.name).append("\" aria-hidden=\"true\"></a>");
      sb.append(title(level + 1, func.name));
      sb.append(func.description);
      sb.append("\n\n");
      if (func.returnType != null) {
        sb.append(typeName(func.returnType)).append(" ");
      }
      sb.append("<code>").append(func.name).append("(");
      Joiner.on(", ")
          .appendTo(
              sb,
              Lists.transform(
                  func.params,
                  p ->
                      String.format("<a href=#%s.%s>%s</a>", func.name, p.name, p.name)
                          + (p.defaultValue == null ? "" : "=" + p.defaultValue)));
      sb.append(")</code>\n\n");

      if (!func.params.isEmpty()) {
        sb.append(htmlTitle(level + 2, "Parameters:", String.format("parameters.%s", func.name)));
        sb.append(tableHeader("Parameter", "Description"));
        for (DocParam param : Iterables.filter(func.params, DocParam::isDocumented)) {
          sb.append(
              tableRow(
                  String.format(
                      "<span id=%s.%s href=#%s.%s>%s</span>",
                      func.name, param.name, func.name, param.name, param.name),
                  String.format(
                      "%s<br><p>%s</p>",
                      param.allowedTypes.stream().map(this::typeName).collect(joining(" or ")),
                      param.description)));
        }
        sb.append("\n");
      }
      if (!func.examples.isEmpty()) {
        sb.append(
            htmlTitle(
                level + 2,
                func.examples.size() == 1 ? "Example:" : "Examples:",
                "example." + func.name));
        for (DocExample example : func.examples) {
          sb.append(example(level + 3, example.example));
        }
        sb.append("\n");
      }
      sb.append(flags(func.flags));
    }
    return fixUpBazelDoc(sb);
  }

  // Bazel has some html that assumes a different context, hacky best effort correction
  private String fixUpBazelDoc(StringBuilder doc) {
    String bazelDoc = doc.toString();
    bazelDoc = bazelDoc.replace("../core/set.html", "#set-2");
    bazelDoc = bazelDoc.replace("../globals/all.html", "");
    bazelDoc =
        bazelDoc.replaceAll("(?sm)(?<!(?:</li>|<ol>|<ul>))\\s*(<li>|</ol>|</ul>)", "</li>$1");
    bazelDoc = bazelDoc.replaceAll("(?sm)</li>(\\s*)</li>", "</li>$1");
    bazelDoc = bazelDoc.replaceAll("(?sm)(<[ou]l>)(\\s*)</li>", "$1$2");

    return bazelDoc;
  }

  private String title(int level, String name) {
    return "\n" + Strings.repeat("#", level) + ' ' + name + "\n\n";
  }

  private String htmlTitle(int level, String name, String id) {
    String tag = String.format("h%d", level);
    return String.format("\n<%s id=\"%s\">%s</%s>\n\n", tag, id, name, tag);
  }

  private boolean shouldLinkify(String type) {
    return headings.contains(type);
  }

  private String typeName(String type) {
    return htmlCodify(typeNameHelper(type));
  }

  private String getSequenceElementType(String sequenceType) {
    return sequenceType.substring("sequence of ".length());
  }

  private String getDictKeyType(String dictType) {
    return dictType.substring("dict[".length(), dictType.indexOf(", "));
  }

  private String getDictValueType(String dictType) {
    return dictType.substring(dictType.indexOf(", ") + 2, dictType.indexOf("]"));
  }

  // type name without 'code' formatting applied
  private String typeNameHelper(String type) {
    if (type.startsWith("sequence of ")) {
      return "sequence of " + typeNameHelper(getSequenceElementType(type));
    }

    if (type.startsWith("dict[")) {
      return "dict["
          + typeNameHelper(getDictKeyType(type))
          + ", "
          + typeNameHelper(getDictValueType(type))
          + "]";
    }

    if (shouldLinkify(type)) {
      // use html tags, not markdown links, for correct nesting behavior
      return htmlLinkify(type);
    }
    return type;
  }

  private String linkify(String name) {
    return "[" + name + "](#" + Ascii.toLowerCase(name).replace(".", "").replace("`", "") + ")";
  }

  private String htmlLinkify(String name) {
    String href = "#" + Ascii.toLowerCase(name).replace(".", "").replace("`", "");
    return String.format("<a href=\"%s\">%s</a>", href, name);
  }

  private String htmlCodify(String snippet) {
    return String.format("<code>%s</code>", snippet);
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
      for (DocFlag field : Iterables.filter(flags, DocFlag::isDocumented)) {
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
