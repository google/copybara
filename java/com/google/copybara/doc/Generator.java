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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.copybara.doc.DocBase.DocModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Given a file with list of classes, an output file, and an optional template file, generates a
 * Markdown document with Copybara reference guide.
 */
public final class Generator {

  private static final String TEMPLATE_REPLACEMENT = "<!-- Generated reference here -->";

  private Generator() {}

  public static void main(String[] args) throws IOException {

    List<String> jarNames = Splitter.on(",").omitEmptyStrings().splitToList(args[0]);
    List<DocModule> modules = new ModuleLoader().load(jarNames);
    CharSequence markdown = new MarkdownRenderer().render(modules);

    String outputFile = args[1];

    String template =
        args.length > 2
            ? new String(Files.readAllBytes(Paths.get(args[2])), UTF_8)
            : TEMPLATE_REPLACEMENT;

    String output = template.replace(TEMPLATE_REPLACEMENT, TEMPLATE_REPLACEMENT + "\n" + markdown);

    Files.write(Paths.get(outputFile), ImmutableList.of(output));
  }


}
