/*
 * Copyright (C) 2016 Google Inc.
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

using System.Collections.Immutable;
using System.Text.RegularExpressions;
using Copybara.Common;
using Copybara.Exceptions;
using Copybara.TemplateToken;

namespace Copybara;

/// <summary>
/// A simple line finder/parser for labels like:
/// <list type="bullet">
///   <item><description>foo = bar</description></item>
///   <item><description>baz : foo</description></item>
/// </list>
///
/// <para>In general this class should only be used in <c>Origin</c>s to create a labels map. During
/// transformations/destination, it can be used to check if a line is a label but never to find
/// labels. Use <see cref="TransformWork.GetLabel(string)"/> instead, since it looks in more places
/// for labels.</para>
/// </summary>
public class LabelFinder
{
    private const string ValidLabelExpr = "([\\w-]+)";

    public static readonly Regex VALID_LABEL = new("^" + ValidLabelExpr + "$", RegexOptions.Compiled);

    private static readonly Regex LabelVar =
        new("^\\$\\{(" + ValidLabelExpr + ")}$", RegexOptions.Compiled);

    private static readonly Regex Url = new("^" + ValidLabelExpr + "://.*", RegexOptions.Compiled);

    private static readonly Regex LabelPattern =
        new("^" + ValidLabelExpr + "( *[:=] ?)(.*)$", RegexOptions.Compiled | RegexOptions.Singleline);

    private readonly Match _match;
    private readonly string _line;

    public LabelFinder(string line)
    {
        _match = LabelPattern.Match(line);
        _line = line;
    }

    /// <summary>A utility for resolving list of string labels to values.</summary>
    public static IReadOnlyList<string> MapLabels(
        Func<string, IReadOnlyCollection<string>?> labelsMapper, IReadOnlyList<string> list)
    {
        var result = ImmutableArray.CreateBuilder<string>();
        foreach (var element in list)
        {
            var match = LabelVar.Match(element);
            if (!match.Success)
            {
                result.Add(element);
                continue;
            }
            var label = match.Groups[1].Value;
            var values = labelsMapper(label);
            if (values == null)
            {
                continue;
            }
            result.AddRange(values);
        }
        return result.ToImmutable();
    }

    public static string MapLabels(
        Func<string, IReadOnlyCollection<string>?> labelsMapper, string template) =>
        MapLabels(labelsMapper, template, null);

    public static string MapLabels(
        Func<string, IReadOnlyCollection<string>?> labelsMapper, string template, string? fieldName)
    {
        try
        {
            return new LabelTemplate(template).Resolve(label =>
            {
                var values = labelsMapper(label);
                if (values == null)
                {
                    return null;
                }
                foreach (var v in values)
                {
                    return v;
                }
                return null;
            });
        }
        catch (LabelTemplate.LabelNotFoundException e)
        {
            throw new ValidationException(
                $"Cannot find '{e.Label}' label for template '{template}' defined in field '{fieldName}'",
                e);
        }
    }

    public bool IsLabel()
    {
        // It is a label if it looks like a label but it doesn't look like a url (foo://bar)
        return _match.Success && !Url.Match(_line).Success;
    }

    public bool IsLabel(string labelName) => IsLabel() && GetName().Equals(labelName);

    /// <summary>
    /// Returns the name of the label.
    ///
    /// <para>Use <see cref="IsLabel()"/> before calling this method.</para>
    /// </summary>
    public string GetName()
    {
        CheckIsLabel();
        return _match.Groups[1].Value;
    }

    /// <summary>
    /// Returns the separator of the label.
    ///
    /// <para>Use <see cref="IsLabel()"/> before calling this method.</para>
    /// </summary>
    public string GetSeparator()
    {
        CheckIsLabel();
        return _match.Groups[2].Value;
    }

    /// <summary>
    /// Returns the value of the label.
    ///
    /// <para>Use <see cref="IsLabel()"/> before calling this method.</para>
    /// </summary>
    public string GetValue()
    {
        CheckIsLabel();
        return _match.Groups[3].Value;
    }

    private void CheckIsLabel() =>
        Preconditions.CheckState(IsLabel(), "Not a label: '{0}'. Please call isLabel() first", _line);

    public string GetLine() => _line;
}
