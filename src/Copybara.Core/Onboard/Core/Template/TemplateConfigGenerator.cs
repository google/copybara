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

using System.Text.RegularExpressions;

using Copybara.Util;

namespace Copybara.Onboard.Core.Template;

/// <summary>
/// A config generator that uses a template for generating the config. Template fields can be in two
/// forms:
/// <list type="bullet">
/// <item>NAMED fields: Text like <c>::field_name::</c> is replaced with the value.</item>
/// <item>KEYWORD fields: If the template has the literal <c>::keyword_params::</c>, it is replaced
/// with a list of the keyword params.</item>
/// </list>
/// Port of <c>com.google.copybara.onboard.core.template.TemplateConfigGenerator</c>.
/// </summary>
public abstract class TemplateConfigGenerator : IConfigGenerator
{
    private static readonly Regex LoadStatements = new("::load_statements::");
    private static readonly Regex NamedField = new("::[A-Za-z0-9_-]+::");
    private static readonly Regex Keyword = new("([\t ]*)::keyword_params::");

    private readonly string _template;
    private readonly SortedDictionary<string, SortedSet<string>> _libraryToIncludes =
        new(StringComparer.Ordinal);

    protected TemplateConfigGenerator(string template)
    {
        _template = template;
    }

    public abstract string Name { get; }

    public abstract IReadOnlySet<IInput> Consumes();

    public abstract bool IsGenerator(IInputProviderResolver resolver);

    protected void AddLoadStatement(string library, string include)
    {
        if (!_libraryToIncludes.TryGetValue(library, out SortedSet<string>? set))
        {
            set = new SortedSet<string>(StringComparer.Ordinal);
            _libraryToIncludes[library] = set;
        }

        set.Add($"'{include}'");
    }

    private string GenerateLoadStatements()
    {
        var allLoadStatements = new List<string>();
        foreach (KeyValuePair<string, SortedSet<string>> entry in _libraryToIncludes)
        {
            allLoadStatements.Add(
                $"load('{entry.Key}', {string.Join(", ", entry.Value)})");
        }

        return string.Join("\n", allLoadStatements);
    }

    /// <exception cref="CannotProvideException"/>
    /// <exception cref="System.Threading.ThreadInterruptedException"/>
    public string Generate(IInputProviderResolver resolver)
    {
        IReadOnlySet<IInput> consumes = Consumes();
        IReadOnlyDictionary<Field, object> fields = Resolve(new ConsumesCheckingResolver(consumes, resolver));

        string config = _template;
        // TODO - b/326285980: Handle field values when they are the same format as the named field
        // templates, e.g. ::foo::.
        foreach (KeyValuePair<Field, object> e in fields)
        {
            if (e.Key.Location == FieldLocation.Named)
            {
                config = SetNamedParam(config, e.Key, e.Value);
            }
        }

        Match keywordMatch = Keyword.Match(config);
        if (keywordMatch.Success)
        {
            string spaces = keywordMatch.Groups[1].Value;
            string replacement = string.Join(
                "\n",
                fields.Keys
                    .Where(x => x.Location == FieldLocation.Keyword)
                    .Select(x => $"{spaces}{x.Name} = {fields[x]},"));
            config = ReplaceFirst(config, Keyword, replacement);
        }

        Match loadMatch = LoadStatements.Match(config);
        if (loadMatch.Success)
        {
            config = ReplaceFirst(config, LoadStatements, GenerateLoadStatements());
        }

        var notReplaced = new HashSet<string>();
        var templateFieldTokens =
            fields.Keys.Select(f => $"::{f.Name}::").ToHashSet(StringComparer.Ordinal);
        foreach (Match m in NamedField.Matches(config))
        {
            string field = m.Value;
            // We only want to include named field matches that were present in the original template.
            if (templateFieldTokens.Contains(field))
            {
                notReplaced.Add(field);
            }
        }

        if (notReplaced.Count != 0)
        {
            throw new InvalidOperationException(
                "The following template variables are not being set with values: ["
                    + string.Join(", ", notReplaced) + "]");
        }

        return config;
    }

    private static string ReplaceFirst(string input, Regex regex, string replacement)
    {
        Match m = regex.Match(input);
        if (!m.Success)
        {
            return input;
        }

        return input.Substring(0, m.Index) + replacement + input.Substring(m.Index + m.Length);
    }

    private static string SetNamedParam(string config, Field field, object value)
    {
        if (!config.Contains(field.Name))
        {
            throw new InvalidOperationException(
                $"Named parameter {field.Name} not used in this template. Consider using"
                    + " setStringKeywordParameter instead.");
        }

        string replace = config.Replace($"::{field.Name}::", $"{value}");

        if (field.Required && replace.Equals(config, StringComparison.Ordinal))
        {
            throw new InvalidOperationException($"::{field.Name}:: not found in template");
        }

        return replace;
    }

    /// <summary>
    /// Useful for keyword fields that we want to represent as string literals. <see cref="Resolve"/>
    /// can return a value wrapped like this for keyword field values that should be printed as
    /// <c>foo = "value"</c> (with quotes).
    /// </summary>
    protected string KeywordStringLiteral(string value) => "\"" + value + "\"";

    /// <summary>Buildifier won't format lists with newlines unless at least one is on a new line.</summary>
    protected string GlobToStringWithNewline(Glob glob)
    {
        string asString = glob.ToString();
        // Skip for a common glob case.
        if (asString.Equals("glob(include = [\"**\"])", StringComparison.Ordinal))
        {
            return asString;
        }

        if (asString.Contains("[\""))
        {
            return asString.Replace("[\"", "[\n                 \"");
        }

        return asString;
    }

    /// <summary>
    /// Calls <c>ToString</c> on the collection and adds a newline after the first open bracket, which
    /// forces Buildifier to format the collection as a multi-line list.
    /// </summary>
    protected string CollectionToStringWithNewline<T>(IReadOnlyCollection<T> collection)
    {
        // No need to add a newline if the collection is a single element.
        if (collection.Count <= 1)
        {
            return CollectionToJavaString(collection);
        }

        string s = CollectionToJavaString(collection);
        int idx = s.IndexOf('[');
        return idx < 0 ? s : s.Substring(0, idx) + "[\n" + s.Substring(idx + 1);
    }

    private static string CollectionToJavaString<T>(IEnumerable<T> collection) =>
        "[" + string.Join(", ", collection) + "]";

    /// <summary>
    /// Converts a boolean to a string that represents the same value in Starlark (<c>True</c>/
    /// <c>False</c>).
    /// </summary>
    protected string ConvertBooleanToStarlarkBoolean(bool value) => value ? "True" : "False";

    /// <summary>
    /// Method to be implemented by the specific templates to provide the field values using
    /// <see cref="IInputProviderResolver"/>.
    /// </summary>
    /// <exception cref="CannotProvideException"/>
    /// <exception cref="System.Threading.ThreadInterruptedException"/>
    protected abstract IReadOnlyDictionary<Field, object> Resolve(IInputProviderResolver resolver);

    public override string ToString() => Name;

    /// <summary>
    /// Delegating resolver that fails if a template resolves an input not declared in
    /// <c>consumes()</c>. Mirrors the anonymous inner class in Java's <c>generate</c>.
    /// </summary>
    private sealed class ConsumesCheckingResolver : IInputProviderResolver
    {
        private readonly IReadOnlySet<IInput> _consumes;
        private readonly IInputProviderResolver _delegate;

        public ConsumesCheckingResolver(IReadOnlySet<IInput> consumes, IInputProviderResolver @delegate)
        {
            _consumes = consumes;
            _delegate = @delegate;
        }

        public T Resolve<T>(Input<T> input)
            where T : class
        {
            if (!_consumes.Contains(input))
            {
                throw new InvalidOperationException(
                    $"Non-declared input in template: {input}. Add it to consumes() method");
            }

            return _delegate.Resolve(input);
        }

        public IReadOnlyDictionary<string, IConfigGenerator> GetGenerators() =>
            _delegate.GetGenerators();

        public T ParseStarlark<T>(string starlark)
            where T : class =>
            _delegate.ParseStarlark<T>(starlark);
    }
}
