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

    List<String> classNames = Files.readAllLines(Paths.get(args[0]), UTF_8);
    List<DocModule> modules = new ModuleLoader().load(classNames);
    CharSequence markdown = new MarkdownRenderer().render(modules);

    String template =
        args.length > 1
            ? new String(Files.readAllBytes(Paths.get(args[1])), UTF_8)
            : TEMPLATE_REPLACEMENT;

    System.out.println(
        template.replace(TEMPLATE_REPLACEMENT, TEMPLATE_REPLACEMENT + "\n" + markdown));
  }


}
