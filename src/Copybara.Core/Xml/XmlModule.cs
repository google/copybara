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

using System.Xml;
using System.Xml.XPath;
using Copybara.Config;
using Copybara.Exceptions;
using Starlark.Annot;
using Starlark.Eval;

namespace Copybara.Xml;

/// <summary>Set of functions to work with XML in Copybara.</summary>
/// <remarks>
/// NOTE(port): upstream uses javax.xml + javax.xml.xpath. This port uses the in-box
/// <see cref="System.Xml"/> / <see cref="System.Xml.XPath"/> APIs.
/// </remarks>
[StarlarkBuiltin("xml", Doc = "Set of functions to work with XML in Copybara.")]
public sealed class XmlModule : IStarlarkValue
{
    [StarlarkMethod("xpath", Doc = "Run an xpath expression")]
    public object Compile(
        [Param(Name = "content", Doc = "The XML content", Named = true)]
        string xmlContent,
        [Param(Name = "expression", Doc = "XPath expression", Named = true)]
        string expression,
        [Param(Name = "type", Named = true,
            Doc = "The type of the return value, see http://www.w3.org/TR/xpath"
                + "for more details. For now we support STRING, BOOLEAN & NUMBER.")]
        string type)
    {
        XmlDocument xmlDocument = new() { XmlResolver = null };
        try
        {
            xmlDocument.LoadXml(xmlContent);

            var navigator = xmlDocument.CreateNavigator()!;
            var xpathType = SkylarkUtil.StringToEnum<XPathTypes>("type", type);
            var expr = navigator.Compile(expression);

            return xpathType switch
            {
                XPathTypes.STRING => Evaluate(navigator, expr, XPathResultType.String),
                XPathTypes.NUMBER => Evaluate(navigator, expr, XPathResultType.Number),
                XPathTypes.BOOLEAN => Evaluate(navigator, expr, XPathResultType.Boolean),
                _ => throw new ValidationException($"Unsupported xpath type: {type}"),
            };
        }
        catch (Exception e) when (e is XmlException or XPathException or EvalException)
        {
            throw new ValidationException("Error parsing XML", e);
        }
    }

    private static object Evaluate(XPathNavigator navigator, XPathExpression expr,
        XPathResultType returnType)
    {
        expr.SetContext(new XmlNamespaceManager(new NameTable()));
        object result = navigator.Evaluate(expr);

        // Match javax.xml.xpath coercion semantics for STRING/NUMBER/BOOLEAN result types.
        return returnType switch
        {
            XPathResultType.String => Convert.ToString(
                result, System.Globalization.CultureInfo.InvariantCulture) ?? "",
            XPathResultType.Number => Convert.ToDouble(
                result, System.Globalization.CultureInfo.InvariantCulture),
            XPathResultType.Boolean => Convert.ToBoolean(result),
            _ => result,
        };
    }

    private enum XPathTypes
    {
        STRING,
        NUMBER,
        BOOLEAN,
    }
}
