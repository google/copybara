/*
 * Copyright (C) 2023 Google LLC.
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

package com.google.copybara.html;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.copybara.exception.ValidationException;
import java.util.List;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.StarlarkValue;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** Starlark methods for working with HTML content. */
@StarlarkBuiltin(name = "html", doc = "Set of functions to work with HTML in copybara")
public class HtmlModule implements StarlarkValue {

  @StarlarkMethod(
      name = "xpath",
      doc = "Run an xpath expression on HTML content to select elements. This only supports"
          + " a subset of xpath expressions.",
      parameters = {
        @Param(name = "content", doc = "The HTML content", named = true),
        @Param(name = "expression", doc = "XPath expression to select elements", named = true),
      })
  public List<HtmlElement> selectElements(String htmlContent, String expression)
      throws ValidationException {
    Document doc = Jsoup.parse(htmlContent);
    return doc.selectXpath(expression, Element.class).stream()
        .map(HtmlElement::new)
        .collect(toImmutableList());
  }
}
