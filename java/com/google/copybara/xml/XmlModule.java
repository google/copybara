/*
 * Copyright (C) 2022 Google Inc.
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

package com.google.copybara.xml;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.copybara.config.SkylarkUtil;
import com.google.copybara.exception.ValidationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.StarlarkValue;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/** Starlark functions for parsing and querying XML documents in Copybara. */
@StarlarkBuiltin(name = "xml", doc = "Set of functions to work with XML in Copybara.")
public class XmlModule implements StarlarkValue {

  /**
   * Evaluates an XPath 1.0 expression against {@code xmlContent} and returns the typed value.
   *
   * <p>{@code DOCTYPE} declarations, external entities, and XInclude processing are disabled.
   *
   * @param xmlContent UTF-8 XML document content to parse
   * @param expression XPath 1.0 expression to evaluate against the parsed document
   * @param type target XPath return type ({@code STRING}, {@code NUMBER}, or {@code BOOLEAN})
   * @return the evaluated {@link String}, {@link Double}, or {@link Boolean} result
   * @throws ValidationException if {@code xmlContent} cannot be parsed (including when a {@code
   *     DOCTYPE} declaration is present), {@code expression} is invalid, or {@code type} is unknown
   */
  @StarlarkMethod(
      name = "xpath",
      doc = "Run an xpath expression. DOCTYPE declarations and external entities are disallowed.",
      parameters = {
        @Param(name = "content", doc = "The XML content", named = true),
        @Param(name = "expression", doc = "XPath expression", named = true),
        @Param(
            name = "type",
            named = true,
            doc =
                "The type of the return value, see http://www.w3.org/TR/xpath"
                    + "for more details. For now we support STRING, BOOLEAN & NUMBER.")
      })
  public Object compile(String xmlContent, String expression, String type)
      throws ValidationException {
    DocumentBuilder builder = createDocumentBuilder();
    try {
      Document xmlDocument = builder.parse(new ByteArrayInputStream(xmlContent.getBytes(UTF_8)));

      return SkylarkUtil.stringToEnum("type", type, XPathTypes.class)
          .evaluate(expression, xmlDocument, XPathFactory.newInstance().newXPath());
    } catch (SAXException | IOException | XPathExpressionException | EvalException e) {
      throw new ValidationException("Error parsing XML", e);
    }
  }

  private static DocumentBuilder createDocumentBuilder() {
    DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
    try {
      builderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      builderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      builderFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      builderFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      builderFactory.setFeature(
          "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      builderFactory.setXIncludeAware(false);
      builderFactory.setExpandEntityReferences(false);
      return builderFactory.newDocumentBuilder();
    } catch (ParserConfigurationException e) {
      throw new IllegalStateException("Error creating document builder for parsing XML", e);
    }
  }

  private enum XPathTypes {
    STRING {
      @Override
      Object evaluate(String expression, Document xmlDocument, XPath xPath)
          throws XPathExpressionException {
        return xPath.compile(expression).evaluate(xmlDocument, XPathConstants.STRING);
      }
    },
    NUMBER {
      @Override
      Object evaluate(String expression, Document xmlDocument, XPath xPath)
          throws XPathExpressionException {
        return xPath.compile(expression).evaluate(xmlDocument, XPathConstants.NUMBER);
      }
    },
    BOOLEAN {
      @Override
      Object evaluate(String expression, Document xmlDocument, XPath xPath)
          throws XPathExpressionException {
        return xPath.compile(expression).evaluate(xmlDocument, XPathConstants.BOOLEAN);
      }
    };

    abstract Object evaluate(String expression, Document xmlDocument, XPath xPath)
        throws XPathExpressionException;
  }
}
