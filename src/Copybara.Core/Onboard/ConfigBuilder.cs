/*
 * Copyright (C) 2021 Google Inc.
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

namespace Copybara.Onboard;

/// <summary>
/// A class that makes usable config strings from <see cref="IConfigTemplate"/> objects. Port of
/// <c>com.google.copybara.onboard.ConfigBuilder</c>.
/// </summary>
public class ConfigBuilder
{
    private readonly IConfigTemplate _configTemplate;
    private string _configInProgress;
    private readonly Dictionary<string, string> _keywordParams = new();

    public ConfigBuilder(IConfigTemplate configTemplate)
    {
        _configTemplate = configTemplate;
        _configInProgress = configTemplate.GetTemplateString();
    }

    public IReadOnlySet<RequiredField> GetRequiredFields() =>
        _configTemplate.GetRequiredFields().ToImmutableHashSet();

    public void SetNamedStringParameter(string name, string value)
    {
        if (!_configInProgress.Contains(name))
        {
            throw new InvalidOperationException(
                $"Named parameter {name} not used in this template. Consider using"
                    + " setStringKeywordParameter instead.");
        }

        _configInProgress = _configInProgress.Replace($"::{name}::", $"\"{value}\"");
    }

    public void SetNamedStarlarkParameter(string name, string starlark)
    {
        _configInProgress = _configInProgress.Replace($"::{name}::", starlark);
    }

    public void AddStringKeywordParameter(string name, string value)
    {
        _keywordParams[name] = value;
    }

    public string Build()
    {
        _configInProgress =
            _configInProgress.Replace(
                "::keyword_params::",
                string.Join(
                    "\n",
                    _keywordParams.Keys.Select(x => $"    {x}='{_keywordParams[x]}',")));

        if (!_configTemplate.Validate(_configInProgress))
        {
            throw new InvalidOperationException(
                string.Format(
                    "Config is not valid.\n\nConfig: {0}\n\nRequired Fields: [{1}]",
                    _configInProgress,
                    string.Join(", ", _configTemplate.GetRequiredFields().Select(f => f.Name))));
        }

        return _configInProgress;
    }

    public bool IsValid() => _configTemplate.Validate(_configInProgress);
}
