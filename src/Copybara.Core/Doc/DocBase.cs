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
using Copybara.Doc.Annotations;

namespace Copybara.Doc;

/// <summary>
/// Helper for generating documentation from the Starlark annotations in the Copybara codebase. Port
/// of <c>com.google.copybara.doc.DocBase</c>.
/// </summary>
public abstract class DocBase : IComparable<DocBase>
{
    protected DocBase(string name, string description, bool isDocumented)
    {
        Name = name ?? throw new ArgumentNullException(nameof(name));
        Description = description ?? throw new ArgumentNullException(nameof(description));
        IsDocumented = isDocumented;
    }

    public string Name { get; }

    public string Description { get; }

    public bool IsDocumented { get; }

    public int CompareTo(DocBase? other)
    {
        if (other is null)
        {
            return 1;
        }
        return string.CompareOrdinal(Name, other.Name);
    }

    private static string HandleType(string? type) => type ?? "NoneType";

    /// <summary>Module level documentation node.</summary>
    public sealed class DocModule : DocBase
    {
        public DocModule(string name, string description, bool isDocumented)
            : base(name, description, isDocumented)
        {
        }

        // TreeSet in Java -> SortedSet with the natural (name-based) ordering of DocBase.
        public SortedSet<DocField> Fields { get; } = new(DocBaseComparer.Instance);

        public SortedSet<DocFunction> Functions { get; } = new(DocBaseComparer.Instance);

        public SortedSet<DocFlag> Flags { get; } = new(DocBaseComparer.Instance);

        public override string ToString() => $"DocModule{{name={Name}}}";

        public ImmutableHashSet<DocFunction> GetFunctions() => Functions.ToImmutableHashSet();

        public ImmutableHashSet<DocField> GetFields() => Fields.ToImmutableHashSet();
    }

    /// <summary>Command line flag documentation node.</summary>
    public sealed class DocFlag : DocBase
    {
        public DocFlag(string name, string type, string description, bool isDocumented)
            : base(name, description, isDocumented)
        {
            Type = type;
        }

        public string Type { get; }
    }

    /// <summary>Function level documentation node.</summary>
    public sealed class DocFunction : DocBase
    {
        public DocFunction(
            string name,
            string description,
            string? returnType,
            IEnumerable<DocParam> parameters,
            IEnumerable<DocFlag> flags,
            IEnumerable<DocExample> examples,
            bool hasStar,
            bool hasStarStar,
            bool isSelfCall,
            bool isDocumented)
            : base(name, description, isDocumented)
        {
            ReturnType = returnType;
            Params = parameters.ToImmutableArray();
            Examples = examples.ToImmutableArray();
            HasStar = hasStar;
            HasStarStar = hasStarStar;
            IsSelfCall = isSelfCall;
            foreach (DocFlag flag in flags)
            {
                Flags.Add(flag);
            }
        }

        public SortedSet<DocFlag> Flags { get; } = new(DocBaseComparer.Instance);

        public string? ReturnType { get; }

        public ImmutableArray<DocParam> Params { get; }

        public ImmutableArray<DocExample> Examples { get; }

        public bool HasStar { get; }

        public bool HasStarStar { get; }

        public bool IsSelfCall { get; }

        public IReadOnlyList<DocParam> GetParams() => Params;

        public string GetReturnType() => HandleType(ReturnType);
    }

    /// <summary>Function parameter level documentation node.</summary>
    public sealed class DocParam : DocBase
    {
        public DocParam(
            string name,
            string? defaultValue,
            IReadOnlyList<string> allowedTypes,
            string description,
            bool isDocumented)
            : base(name, description, isDocumented)
        {
            DefaultValue = defaultValue;
            AllowedTypes = allowedTypes;
        }

        public string? DefaultValue { get; }

        public IReadOnlyList<string> AllowedTypes { get; }

        public IReadOnlyList<string> GetAllowedTypes() => AllowedTypes;
    }

    /// <summary>Wrapper around an <see cref="ExampleAttribute"/> for a documented element.</summary>
    public sealed class DocExample
    {
        public DocExample(ExampleAttribute example) => Example = example;

        public ExampleAttribute Example { get; }
    }

    /// <summary>Field level documentation node.</summary>
    public sealed class DocField : DocBase
    {
        public DocField(string name, string description, string? type, bool isDocumented)
            : base(name, description, isDocumented)
        {
            Type = type;
        }

        /// <summary>Raw (possibly null) Starlark type name.</summary>
        public string? Type { get; }

        /// <summary>Type name with null normalized to <c>NoneType</c> (Java's <c>getType()</c>).</summary>
        public string GetResolvedType() => HandleType(Type);
    }

    /// <summary>Comparer that orders <see cref="DocBase"/> instances by name (Java natural order).</summary>
    internal sealed class DocBaseComparer : IComparer<DocBase>
    {
        internal static readonly DocBaseComparer Instance = new();

        public int Compare(DocBase? x, DocBase? y)
        {
            if (ReferenceEquals(x, y))
            {
                return 0;
            }
            if (x is null)
            {
                return -1;
            }
            return x.CompareTo(y);
        }
    }
}
