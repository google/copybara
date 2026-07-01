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
using System.Text;
using System.Text.RegularExpressions;
using Copybara.Doc.Annotations;
using static Copybara.Doc.DocBase;

namespace Copybara.Doc;

/// <summary>
/// Renders a collection of <see cref="DocModule"/> nodes into the Copybara reference Markdown. Port
/// of <c>com.google.copybara.doc.MarkdownRenderer</c>.
/// </summary>
internal sealed class MarkdownRenderer
{
    private const int ModuleHeadingLevel = 2;

    private const string SequenceOfPrefix = "sequence of ";

    private readonly HashSet<string> headings = new();

    private readonly Dictionary<string, ImmutableHashSet<string>> returnedBy = new();

    private readonly Dictionary<string, ImmutableHashSet<string>> consumedBy = new();

    private static void AddToMapValueSet(
        Dictionary<string, ImmutableHashSet<string>> map, string key, string value)
    {
        ImmutableHashSet<string> existing =
            map.TryGetValue(key, out ImmutableHashSet<string>? set) ? set : ImmutableHashSet<string>.Empty;
        map[key] = existing.Add(value);
    }

    public string Render(IEnumerable<DocModule> modules, bool includeFlagAggregate)
    {
        var materialized = modules.ToList();
        var modulesToRender = new List<DocModule>();
        modulesToRender.AddRange(materialized.Where(m => m.IsDocumented));
        if (includeFlagAggregate)
        {
            modulesToRender.Add(RenderFlags(materialized));
        }

        PopulateUsageMaps(modulesToRender);

        var sb = new StringBuilder();
        sb.Append(TableOfContents(modulesToRender));

        foreach (DocModule module in modulesToRender)
        {
            sb.Append('\n');
            sb.Append(RenderModule(module, ModuleHeadingLevel));
        }
        return sb.ToString();
    }

    private void PopulateUsageMaps(IEnumerable<DocModule> modules)
    {
        foreach (DocModule module in modules.Where(m => m.IsDocumented))
        {
            foreach (DocFunction f in module.Functions.Where(x => x.IsDocumented))
            {
                if (f.ReturnType != null)
                {
                    AddToMapValueSet(returnedBy, f.ReturnType, f.Name);

                    if (f.ReturnType.StartsWith(SequenceOfPrefix, StringComparison.Ordinal))
                    {
                        AddToMapValueSet(returnedBy, GetSequenceElementType(f.ReturnType), f.Name);
                    }
                    if (f.ReturnType.StartsWith("dict[", StringComparison.Ordinal))
                    {
                        AddToMapValueSet(returnedBy, GetDictKeyType(f.ReturnType), f.Name);
                        AddToMapValueSet(returnedBy, GetDictValueType(f.ReturnType), f.Name);
                    }
                }

                foreach (DocParam param in f.Params.Where(x => x.IsDocumented))
                {
                    foreach (string type in param.AllowedTypes)
                    {
                        AddToMapValueSet(consumedBy, type, f.Name);

                        if (type.StartsWith(SequenceOfPrefix, StringComparison.Ordinal))
                        {
                            AddToMapValueSet(consumedBy, GetSequenceElementType(type), f.Name);
                        }
                        if (type.StartsWith("dict[", StringComparison.Ordinal))
                        {
                            AddToMapValueSet(consumedBy, GetDictKeyType(type), f.Name);
                            AddToMapValueSet(consumedBy, GetDictValueType(type), f.Name);
                        }
                    }
                }
            }
        }
    }

    private DocModule RenderFlags(IEnumerable<DocModule> modules)
    {
        var flagSet = new SortedSet<DocFlag>(DocBaseComparer.Instance);
        foreach (DocModule module in modules.Where(m => m.IsDocumented))
        {
            foreach (DocFlag flag in module.Flags)
            {
                flagSet.Add(flag);
            }
        }
        var flagModule =
            new DocModule("copybara_flags", "All flag options available to the Copybara CLI.", true);
        foreach (DocFlag flag in flagSet)
        {
            flagModule.Flags.Add(flag);
        }
        return flagModule;
    }

    private string TableOfContents(IEnumerable<DocModule> modules)
    {
        var sb = new StringBuilder();
        sb.Append("## Table of Contents\n\n\n");
        foreach (DocModule module in modules)
        {
            headings.Add(module.Name);
            sb.Append("  - ");
            sb.Append(Linkify(module.Name));
            sb.Append('\n');
            foreach (DocFunction f in module.Functions.Where(x => x.IsDocumented))
            {
                headings.Add(f.Name);
                sb.Append("    - ");
                sb.Append(Linkify(f.Name));
                sb.Append('\n');
            }
        }
        sb.Append('\n');
        return sb.ToString();
    }

    private string RenderModule(DocModule module, int level)
    {
        var sb = new StringBuilder();
        sb.Append(Title(level, module.Name));
        sb.Append(module.Description).Append("\n\n");

        if (module.Fields.Count > 0)
        {
            sb.Append(HtmlTitle(level + 2, "Fields:", "fields." + module.Name));
            sb.Append(TableHeader("Name", "Description"));
            foreach (DocField field in module.Fields)
            {
                sb.Append(
                    TableRow(
                        field.Name,
                        $"{TypeName(field.GetResolvedType())}<br><p>{field.Description}</p>"));
            }
            sb.Append('\n');
        }

        sb.Append(RenderFlagsTable(module.Flags));

        ImmutableHashSet<string> moduleReturnedBy = GetOrEmpty(returnedBy, module.Name);
        if (!moduleReturnedBy.IsEmpty)
        {
            sb.Append(HtmlTitle(level + 2, "Returned By:", "returned_by." + module.Name));
            sb.Append("<ul>");
            foreach (string funcName in moduleReturnedBy)
            {
                sb.Append($"<li><a href=\"#{funcName}\">{funcName}</a></li>");
            }
            sb.Append("</ul>");
        }
        ImmutableHashSet<string> moduleConsumedBy = GetOrEmpty(consumedBy, module.Name);
        if (!moduleConsumedBy.IsEmpty)
        {
            sb.Append(HtmlTitle(level + 2, "Consumed By:", "consumed_by." + module.Name));
            sb.Append("<ul>");
            foreach (string funcName in moduleConsumedBy)
            {
                sb.Append($"<li><a href=\"#{funcName}\">{funcName}</a></li>");
            }
            sb.Append("</ul>");
        }

        if (!moduleReturnedBy.IsEmpty || !moduleConsumedBy.IsEmpty)
        {
            sb.Append("\n\n");
        }

        foreach (DocFunction func in module.Functions.Where(x => x.IsDocumented))
        {
            sb.Append("<a id=\"").Append(func.Name).Append("\" aria-hidden=\"true\"></a>");
            sb.Append(Title(level + 1, func.Name));
            sb.Append(func.Description);
            sb.Append("\n\n");
            if (func.ReturnType != null)
            {
                sb.Append(TypeName(func.ReturnType)).Append(' ');
            }
            sb.Append("<code>").Append(func.Name).Append('(');
            sb.Append(
                string.Join(
                    ", ",
                    func.Params.Select(
                        p =>
                            $"<a href=#{func.Name}.{p.Name}>{p.Name}</a>"
                                + (p.DefaultValue == null ? "" : "=" + p.DefaultValue))));
            sb.Append(")</code>\n\n");

            if (func.Params.Length > 0)
            {
                sb.Append(HtmlTitle(level + 2, "Parameters:", $"parameters.{func.Name}"));
                sb.Append(TableHeader("Parameter", "Description"));
                foreach (DocParam param in func.Params.Where(x => x.IsDocumented))
                {
                    sb.Append(
                        TableRow(
                            $"<span id={func.Name}.{param.Name} href=#{func.Name}.{param.Name}>{param.Name}</span>",
                            $"{string.Join(" or ", param.AllowedTypes.Select(TypeName))}<br><p>{param.Description}</p>"));
                }
                sb.Append('\n');
            }
            if (func.Examples.Length > 0)
            {
                sb.Append(
                    HtmlTitle(
                        level + 2,
                        func.Examples.Length == 1 ? "Example:" : "Examples:",
                        "example." + func.Name));
                foreach (DocExample example in func.Examples)
                {
                    sb.Append(RenderExample(level + 3, example.Example));
                }
                sb.Append('\n');
            }
            sb.Append(RenderFlagsTable(func.Flags));
        }
        return FixUpBazelDoc(sb);
    }

    // Bazel has some html that assumes a different context, hacky best effort correction.
    private static string FixUpBazelDoc(StringBuilder doc)
    {
        string bazelDoc = doc.ToString();
        bazelDoc = bazelDoc.Replace("../core/set.html", "#set-2");
        bazelDoc = bazelDoc.Replace("../globals/all.html", "");
        bazelDoc = Regex.Replace(
            bazelDoc,
            @"(?<!(?:</li>|<ol>|<ul>))\s*(<li>|</ol>|</ul>)",
            "</li>$1",
            RegexOptions.Singleline | RegexOptions.Multiline);
        bazelDoc = Regex.Replace(
            bazelDoc, @"</li>(\s*)</li>", "</li>$1", RegexOptions.Singleline | RegexOptions.Multiline);
        bazelDoc = Regex.Replace(
            bazelDoc, @"(<[ou]l>)(\s*)</li>", "$1$2", RegexOptions.Singleline | RegexOptions.Multiline);
        return bazelDoc;
    }

    private static string Title(int level, string name) => "\n" + new string('#', level) + ' ' + name + "\n\n";

    private static string HtmlTitle(int level, string name, string id)
    {
        string tag = $"h{level}";
        return $"\n<{tag} id=\"{id}\">{name}</{tag}>\n\n";
    }

    private bool ShouldLinkify(string type) => headings.Contains(type);

    private string TypeName(string type) => HtmlCodify(TypeNameHelper(type));

    private static string GetSequenceElementType(string sequenceType) =>
        sequenceType.Substring(SequenceOfPrefix.Length);

    private static string GetDictKeyType(string dictType) =>
        dictType.Substring("dict[".Length, dictType.IndexOf(", ", StringComparison.Ordinal) - "dict[".Length);

    private static string GetDictValueType(string dictType) =>
        dictType.Substring(
            dictType.IndexOf(", ", StringComparison.Ordinal) + 2,
            dictType.IndexOf(']') - (dictType.IndexOf(", ", StringComparison.Ordinal) + 2));

    // type name without 'code' formatting applied
    private string TypeNameHelper(string type)
    {
        if (type.StartsWith(SequenceOfPrefix, StringComparison.Ordinal))
        {
            return SequenceOfPrefix + TypeNameHelper(GetSequenceElementType(type));
        }

        if (type.StartsWith("dict[", StringComparison.Ordinal))
        {
            return "dict["
                + TypeNameHelper(GetDictKeyType(type))
                + ", "
                + TypeNameHelper(GetDictValueType(type))
                + "]";
        }

        if (ShouldLinkify(type))
        {
            // use html tags, not markdown links, for correct nesting behavior
            return HtmlLinkify(type);
        }
        return type;
    }

    private static string Linkify(string name) =>
        "[" + name + "](#" + name.ToLowerInvariant().Replace(".", "").Replace("`", "") + ")";

    private static string HtmlLinkify(string name)
    {
        string href = "#" + name.ToLowerInvariant().Replace(".", "").Replace("`", "");
        return $"<a href=\"{href}\">{name}</a>";
    }

    private static string HtmlCodify(string snippet) => $"<code>{snippet}</code>";

    private static string RenderExample(int level, ExampleAttribute example)
    {
        var sb = new StringBuilder();
        sb.Append(Title(level, example.Title + ":"));
        sb.Append(example.Before).Append("\n\n");
        sb.Append("```python\n").Append(example.Code).Append("\n```\n\n");
        if (example.After.Length != 0)
        {
            sb.Append(example.After).Append("\n\n");
        }
        return sb.ToString();
    }

    private static string RenderFlagsTable(IEnumerable<DocFlag> flags)
    {
        var list = flags.ToList();
        var sb = new StringBuilder();
        if (list.Count > 0)
        {
            sb.Append("\n\n**Command line flags:**\n\n");
            sb.Append(TableHeader("Name", "Type", "Description"));
            foreach (DocFlag field in list.Where(f => f.IsDocumented))
            {
                sb.Append(TableRow(NoWrap(field.Name), $"*{field.Type}*", field.Description));
            }
            sb.Append('\n');
        }
        return sb.ToString();
    }

    /// <summary>Don't wrap this text. Also use '`' to show it as code.</summary>
    private static string NoWrap(string text) =>
        $"<span style=\"white-space: nowrap;\">`{text}`</span>";

    private static string TableHeader(params string[] fields) =>
        TableRow(fields) + TableRow(fields.Select(e => new string('-', e.Length)).ToArray());

    private static string TableRow(params string[] fields) =>
        string.Join(" | ", fields.Select(s => s.Replace("\n", "<br>"))) + "\n";

    private static ImmutableHashSet<string> GetOrEmpty(
        Dictionary<string, ImmutableHashSet<string>> map, string key) =>
        map.TryGetValue(key, out ImmutableHashSet<string>? set) ? set : ImmutableHashSet<string>.Empty;
}
