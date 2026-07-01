/*
 * Copyright (C) 2018 Google Inc.
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

using System.Text.RegularExpressions;

namespace Copybara.TemplateToken;

/// <summary>
/// A template system that for texts like "This ${LABEL} is a template".
/// TODO(malcon): Consolidate this class and Parser/Token.
/// </summary>
public class LabelTemplate
{
    // ([\w-]+) is coming from LabelFinder.VALID_LABEL_EXPR. Due to a dependency
    // issue we have it here inlined. It is not a big deal as the labels need to exist
    // and also will be refactored into Parser/Token.
    private static readonly Regex VarPattern = new(@"\$\{([\w-]+)}");

    private readonly HashSet<string> _labels = new();
    private readonly string _template;

    /// <summary>
    /// Construct the template object from a String.
    /// </summary>
    /// <param name="template">a String in the form of "Foo ${LABEL} ${OTHER} Bar".</param>
    public LabelTemplate(string template)
    {
        _template = template;
        foreach (Match match in VarPattern.Matches(template))
        {
            _labels.Add(match.Groups[1].Value);
        }
    }

    /// <summary>Resolve the template string for a particular set of labels.</summary>
    /// <exception cref="LabelNotFoundException">if a label cannot be found.</exception>
    public string Resolve(Func<string, string?> labelFinder)
    {
        var labelValues = new Dictionary<string, string>();
        foreach (string label in _labels)
        {
            string? value = labelFinder(label);
            if (value == null)
            {
                throw new LabelNotFoundException(label);
            }
            labelValues[label] = value;
        }

        string result = _template;
        foreach (var entry in labelValues)
        {
            result = result.Replace("${" + entry.Key + "}", entry.Value);
        }
        return result;
    }

    /// <summary>Thrown when a label cannot be found in the message.</summary>
    public class LabelNotFoundException : Exception
    {
        internal LabelNotFoundException(string label)
            : base("Cannot find label " + label)
        {
            Label = label;
        }

        /// <summary>Get the label that couldn't be found.</summary>
        public string Label { get; }
    }
}
