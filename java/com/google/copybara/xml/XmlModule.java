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

import com.google.copybara.config.SkylarkUtil;
import com.google.copybara.exception.ValidationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

/** Regex functions to work with re2 like regexes in Starlark */
@StarlarkBuiltin(name = "xml", doc = "Set of functions to work with XML in Copybara.")
public class XmlModule implements StarlarkValue {

  @StarlarkMethod(
      name = "xpath",
      doc = "Run an xpath expression",
      parameters = {
          @Param(name = "content", doc = "The XML content", named = true),
          @Param(name = "expression", doc = "XPath expression", named = true),
          @Param(name = "type", named = true,
              doc = "The type of the return value, see http://www.w3.org/TR/xpath"
                  + "for more details. For now we support STRING, BOOLEAN & NUMBER.")
      })
  public Object compile(String xmlContent, String expression, String type)
      throws ValidationException {
    DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder;
    try {
      builder = builderFactory.newDocumentBuilder();
    } catch (ParserConfigurationException e) {
      throw new RuntimeException("Error creating document builder for parsing XML");
    }
    try {
      Document xmlDocument = builder.parse(
          new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8)));

      return SkylarkUtil.stringToEnum("type", type, XPathTypes.class)
          .evaluate(expression, xmlDocument, XPathFactory.newInstance().newXPath());
    } catch (SAXException | IOException | XPathExpressionException | EvalException e) {
      throw new ValidationException("Error parsing XML", e);
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
