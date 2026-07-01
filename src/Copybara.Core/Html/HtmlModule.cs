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

using System.Xml;
using System.Xml.XPath;
using Copybara.Exceptions;
using Starlark.Annot;
using Starlark.Eval;

namespace Copybara.Html;

/// <summary>Starlark methods for working with HTML content.</summary>
/// <remarks>
/// NOTE(port): upstream uses jsoup, which provides a full lenient HTML5 parser. To avoid adding a
/// NuGet dependency, this port uses a minimal tolerant HTML-to-XML conversion (see
/// <see cref="HtmlToXml"/>) and evaluates XPath with the in-box <see cref="System.Xml.XPath"/> API.
/// GAPS (TODO): jsoup's full HTML5 tree construction (implied tags, error recovery, entity table,
/// namespace handling) is not reproduced. Only reasonably well-formed HTML with balanced tags is
/// handled; malformed markup that jsoup would repair may fail to parse or select here.
/// </remarks>
[StarlarkBuiltin("html", Doc = "Set of functions to work with HTML in copybara")]
public sealed class HtmlModule : IStarlarkValue
{
    // Void (self-closing) HTML elements that never have a closing tag.
    private static readonly HashSet<string> VoidElements = new(StringComparer.OrdinalIgnoreCase)
    {
        "area", "base", "br", "col", "embed", "hr", "img", "input",
        "link", "meta", "param", "source", "track", "wbr",
    };

    [StarlarkMethod(
        "xpath",
        Doc = "Run an xpath expression on HTML content to select elements. This only supports"
            + " a subset of xpath expressions.")]
    public IReadOnlyList<HtmlElement> SelectElements(
        [Param(Name = "content", Doc = "The HTML content", Named = true)]
        string htmlContent,
        [Param(Name = "expression", Doc = "XPath expression to select elements", Named = true)]
        string expression)
    {
        XmlDocument doc;
        try
        {
            doc = HtmlToXml(htmlContent);
        }
        catch (XmlException e)
        {
            throw new ValidationException("Error parsing HTML", e);
        }

        var results = new List<HtmlElement>();
        try
        {
            var navigator = doc.CreateNavigator()!;
            XPathNodeIterator iterator = navigator.Select(expression);
            while (iterator.MoveNext())
            {
                if (iterator.Current?.UnderlyingObject is XmlElement element)
                {
                    results.Add(new HtmlElement(element));
                }
            }
        }
        catch (XPathException e)
        {
            throw new ValidationException("Error evaluating XPath expression", e);
        }

        return results;
    }

    /// <summary>
    /// Converts (best-effort) an HTML string into an <see cref="XmlDocument"/>. This is a minimal
    /// tolerant parser: it wraps content in a synthetic root, treats known void elements as
    /// self-closing, and drops doctype/comments. It is not a full HTML5 parser.
    /// </summary>
    private static XmlDocument HtmlToXml(string html)
    {
        string xml = Normalize(html);
        var doc = new XmlDocument { XmlResolver = null };
        doc.LoadXml("<__root__>" + xml + "</__root__>");
        return doc;
    }

    private static string Normalize(string html)
    {
        var sb = new System.Text.StringBuilder(html.Length + 16);
        int i = 0;
        int n = html.Length;
        while (i < n)
        {
            char c = html[i];
            if (c != '<')
            {
                // Escape stray ampersands that are not part of an entity, and bare '>'.
                if (c == '&' && !IsEntityStart(html, i))
                {
                    sb.Append("&amp;");
                }
                else
                {
                    sb.Append(c);
                }
                i++;
                continue;
            }

            // Handle comments, doctype, CDATA, declarations: drop them.
            if (html.AsSpan(i).StartsWith("<!--"))
            {
                int end = html.IndexOf("-->", i + 4, StringComparison.Ordinal);
                i = end < 0 ? n : end + 3;
                continue;
            }
            if (i + 1 < n && (html[i + 1] == '!' || html[i + 1] == '?'))
            {
                int end = html.IndexOf('>', i);
                i = end < 0 ? n : end + 1;
                continue;
            }

            int tagEnd = html.IndexOf('>', i);
            if (tagEnd < 0)
            {
                // Unterminated tag; escape the '<' and continue.
                sb.Append("&lt;");
                i++;
                continue;
            }

            string tag = html.Substring(i, tagEnd - i + 1);
            sb.Append(RewriteTag(tag));
            i = tagEnd + 1;
        }

        return sb.ToString();
    }

    private static bool IsEntityStart(string html, int ampIndex)
    {
        int semi = html.IndexOf(';', ampIndex + 1);
        if (semi < 0 || semi - ampIndex > 12)
        {
            return false;
        }
        for (int j = ampIndex + 1; j < semi; j++)
        {
            char c = html[j];
            if (!char.IsLetterOrDigit(c) && c != '#')
            {
                return false;
            }
        }
        return semi > ampIndex + 1;
    }

    private static string RewriteTag(string tag)
    {
        // Closing tag.
        if (tag.StartsWith("</", StringComparison.Ordinal))
        {
            return tag;
        }

        // Extract tag name to detect void elements and normalize to self-closing form.
        int idx = 1;
        while (idx < tag.Length && (char.IsLetterOrDigit(tag[idx]) || tag[idx] == '-'))
        {
            idx++;
        }
        string name = tag.Substring(1, idx - 1);

        bool alreadySelfClosing = tag.EndsWith("/>", StringComparison.Ordinal);
        if (VoidElements.Contains(name) && !alreadySelfClosing)
        {
            return tag.Substring(0, tag.Length - 1) + "/>";
        }

        return tag;
    }
}
