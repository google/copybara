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

import static java.util.Arrays.stream;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.copybara.doc.DocBase.DocModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Given a file with list of classes, an output file, and an optional template file, generates a
 * Markdown document with Copybara reference guide.
 */
public final class Generator {

  private static final String TEMPLATE_REPLACEMENT = "<!-- Generated reference here -->";
  public static final String EXTRA_CLASS = "--extraClass=";
  public static final String TEMPLATE = "--template=";

  private Generator() {}

  public static void main(String[] args) throws IOException {

    ImmutableList<String> additionalClasses =
        stream(args)
            .filter(a -> a.startsWith(EXTRA_CLASS))
            .map(a -> a.replaceFirst(EXTRA_CLASS, ""))
            .map(s -> Splitter.on(',').splitToList(s))
            .map(ImmutableList::copyOf)
            .findFirst()
            .orElse(ImmutableList.of());
    Optional<String> templateFile =
        stream(args)
            .filter(a -> a.startsWith(TEMPLATE))
            .map(a -> a.replaceFirst(TEMPLATE, ""))
            .findFirst();
    List<String> jarNames = Splitter.on(",").omitEmptyStrings().splitToList(args[0]);

    ImmutableList<DocModule> modules = new ModuleLoader().load(jarNames, additionalClasses);
    CharSequence markdown =
        new MarkdownRenderer().render(modules, /* includeFlagAggregate= */ true);

    String outputFile = args[1];

    String template =
        templateFile.isPresent()
            ? Files.readString(Path.of(templateFile.get()))
            : TEMPLATE_REPLACEMENT;

    String output = template.replace(TEMPLATE_REPLACEMENT, TEMPLATE_REPLACEMENT + "\n" + markdown);

    Files.write(Path.of(outputFile), ImmutableList.of(output));
  }


}
