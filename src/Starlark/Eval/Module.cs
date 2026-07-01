// Copyright 2019 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

using System.Collections.Immutable;
using System.Globalization;

namespace Starlark.Eval;

/// <summary>
/// A Module represents a Starlark module, a container of global variables populated by executing a
/// Starlark file. Port of <c>net.starlark.java.eval.Module</c>.
///
/// <para>Note: the resolver integration (<c>Resolver.Module</c>) and static type-checking hooks
/// (<c>getTypeConstructor</c>, <c>get*FieldType</c>) are deferred pending the syntax/type-checker
/// port; the global/predeclared environment functionality is complete.</para>
/// </summary>
public sealed class Module
{
    // The predeclared environment. Excludes UNIVERSE bindings. Conditionally-present values are
    // stored as IGuardedValue regardless of whether they are enabled.
    private readonly ImmutableDictionary<string, object> predeclared;

    // The module's global variables, in order of creation.
    private readonly Dictionary<string, int> globalIndex = new();
    private object?[] globals = new object?[8];

    private readonly object? clientData;
    private readonly StarlarkSemantics semantics;
    private string? documentation;

    private Module(
        ImmutableDictionary<string, object> predeclared, object? clientData, StarlarkSemantics semantics)
    {
        this.predeclared = predeclared;
        this.clientData = clientData;
        this.semantics = semantics;
    }

    /// <summary>Constructs a Module with the specified predeclared bindings.</summary>
    public static Module WithPredeclared(
        StarlarkSemantics semantics, IReadOnlyDictionary<string, object> predeclared) =>
        WithPredeclaredAndData(semantics, predeclared, null);

    /// <summary>Constructs a Module with predeclared bindings and client data.</summary>
    public static Module WithPredeclaredAndData(
        StarlarkSemantics semantics, IReadOnlyDictionary<string, object> predeclared, object? clientData) =>
        new(predeclared.ToImmutableDictionary(), clientData, semantics);

    /// <summary>Creates a module with no predeclared bindings and no client data.</summary>
    public static Module Create() =>
        new(ImmutableDictionary<string, object>.Empty, null, StarlarkSemantics.DEFAULT);

    /// <summary>
    /// Returns the module (file) of the <paramref name="depth"/>-th innermost enclosing Starlark
    /// function on the call stack, or null if the number of active calls that are functions defined
    /// in Starlark is less than or equal to <paramref name="depth"/>.
    ///
    /// <para>This method is a temporary workaround for Starlarkification and should not be used
    /// anywhere else.</para>
    /// </summary>
    public static Module? OfInnermostEnclosingStarlarkFunction(StarlarkThread thread, int depth)
    {
        StarlarkFunction? fn = thread.GetInnermostEnclosingStarlarkFunction(depth);
        return fn?.Module;
    }

    /// <summary>
    /// Returns the module (file) of the innermost enclosing Starlark function on the call stack, or
    /// null if none of the active calls are functions defined in Starlark.
    /// </summary>
    public static Module? OfInnermostEnclosingStarlarkFunction(StarlarkThread thread) =>
        OfInnermostEnclosingStarlarkFunction(thread, 0);

    /// <summary>Returns the client data associated with this module.</summary>
    public object? ClientData => clientData;

    public StarlarkSemantics Semantics => semantics;

    /// <summary>Sets the module's doc string.</summary>
    public void SetDocumentation(string doc) => documentation = doc;

    /// <summary>Returns the module's doc string, or null.</summary>
    public string? GetDocumentation() => documentation;

    // Replaces an enabled IGuardedValue with the value it guards.
    private object FilterGuardedValue(object v)
    {
        if (v is not IGuardedValue gv)
        {
            return v;
        }
        return gv.IsObjectAccessibleUsingSemantics(semantics, clientData) ? gv.GetObject() : gv;
    }

    /// <summary>Returns the value of a predeclared (not universal) binding in this module.</summary>
    public object? GetPredeclared(string name) =>
        predeclared.TryGetValue(name, out object? value) ? FilterGuardedValue(value) : null;

    /// <summary>Returns this module's additional predeclared bindings (excludes UNIVERSE).</summary>
    public IReadOnlyDictionary<string, object> GetPredeclaredBindings()
    {
        var result = new Dictionary<string, object>();
        foreach (var e in predeclared)
        {
            result[e.Key] = FilterGuardedValue(e.Value);
        }
        return result;
    }

    /// <summary>Returns an immutable mapping containing the global variables of this module.</summary>
    public ImmutableDictionary<string, object> GetGlobals()
    {
        var m = ImmutableDictionary.CreateBuilder<string, object>();
        foreach (var e in globalIndex)
        {
            object? v = GetGlobalByIndex(e.Value);
            if (v != null)
            {
                m[e.Key] = v;
            }
        }
        return m.ToImmutable();
    }

    /// <summary>
    /// Returns the value of the specified global variable, or null if not bound. Does not look in
    /// the predeclared environment.
    /// </summary>
    public object? GetGlobal(string name) =>
        globalIndex.TryGetValue(name, out int i) ? globals[i] : null;

    internal void SetGlobalByIndex(int i, object value)
    {
        if (i >= globalIndex.Count)
        {
            throw new ArgumentException("index out of range");
        }
        globals[i] = value;
    }

    internal object? GetGlobalByIndex(int i)
    {
        if (i >= globalIndex.Count)
        {
            throw new ArgumentException("index out of range");
        }
        return globals[i];
    }

    /// <summary>
    /// Returns the index within this Module of a global variable, creating a slot if needed.
    /// </summary>
    internal int GetIndexOfGlobal(string name)
    {
        if (globalIndex.TryGetValue(name, out int prev))
        {
            return prev;
        }
        int i = globalIndex.Count;
        globalIndex[name] = i;
        if (i == globals.Length)
        {
            Array.Resize(ref globals, globals.Length << 1);
        }
        return i;
    }

    /// <summary>Returns a list of indices of a list of globals.</summary>
    internal int[] GetIndicesOfGlobals(IReadOnlyList<string> names)
    {
        int n = names.Count;
        if (n == 0)
        {
            return Array.Empty<int>();
        }
        int[] array = new int[n];
        for (int i = 0; i < n; i++)
        {
            array[i] = GetIndexOfGlobal(names[i]);
        }
        return array;
    }

    /// <summary>Updates a global binding in the module environment.</summary>
    public void SetGlobal(string name, object value)
    {
        ArgumentNullException.ThrowIfNull(value);
        SetGlobalByIndex(GetIndexOfGlobal(name), value);
    }

    public override string ToString() =>
        string.Format(CultureInfo.InvariantCulture, "<module {0}>", clientData?.ToString() ?? "?");
}
