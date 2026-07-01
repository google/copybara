// Copyright 2017 The Bazel Authors. All rights reserved.
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

using System.Collections.Concurrent;
using System.Collections.Immutable;
using System.Reflection;
using Starlark.Annot;

namespace Starlark.Eval;

/// <summary>
/// Helper functions for implementing reflective method dispatch of <see cref="StarlarkMethodAttribute"/>
/// -annotated methods. Port of <c>net.starlark.java.eval.CallUtils</c>.
///
/// <para>Descriptors are computed once per CLR <see cref="Type"/> and cached. Unlike Java, this port
/// does not vary descriptors by <see cref="StarlarkSemantics"/> (flag-guarded methods are not
/// modeled), so a single per-type cache suffices.</para>
/// </summary>
public static class CallUtils
{
    private sealed class ClassDescriptor
    {
        internal MethodDescriptor? SelfCall;
        internal ImmutableDictionary<string, MethodDescriptor> Methods =
            ImmutableDictionary<string, MethodDescriptor>.Empty;
    }

    private static readonly ConcurrentDictionary<Type, ClassDescriptor> Cache = new();

    private static ClassDescriptor GetDescriptor(Type clazz)
    {
        // String methods live on StringModule.
        if (clazz == typeof(string))
        {
            clazz = typeof(StringModule);
        }
        return Cache.GetOrAdd(clazz, BuildClassDescriptor);
    }

    private static ClassDescriptor BuildClassDescriptor(Type clazz)
    {
        var desc = new ClassDescriptor();
        var methods = ImmutableDictionary.CreateBuilder<string, MethodDescriptor>();

        // Deterministic order: sort by name for stability.
        var infos = clazz
            .GetMethods(BindingFlags.Public | BindingFlags.Instance | BindingFlags.FlattenHierarchy)
            .OrderBy(m => m.Name, StringComparer.Ordinal);

        foreach (MethodInfo m in infos)
        {
            var attr = m.GetCustomAttribute<StarlarkMethodAttribute>(inherit: true);
            if (attr == null)
            {
                continue;
            }
            MethodDescriptor md = MethodDescriptor.Of(m, attr);
            if (md.IsSelfCall)
            {
                desc.SelfCall ??= md;
                continue;
            }
            // First declaration wins (base classes appear too via FlattenHierarchy).
            if (!methods.ContainsKey(md.Name))
            {
                methods[md.Name] = md;
            }
        }

        desc.Methods = methods.ToImmutable();
        return desc;
    }

    /// <summary>
    /// Returns a map of methods, keyed by Starlark name, of all <see cref="StarlarkMethodAttribute"/>
    /// -annotated methods of the given class (excluding the selfCall method).
    /// </summary>
    public static ImmutableDictionary<string, MethodDescriptor> GetAnnotatedMethods(Type clazz) =>
        GetDescriptor(clazz).Methods;

    /// <summary>Returns the selfCall method descriptor of the given class, or null.</summary>
    public static MethodDescriptor? GetSelfCallMethodDescriptor(Type clazz) =>
        GetDescriptor(clazz).SelfCall;

    /// <summary>Returns the names of the annotated (non-selfCall) methods of the class.</summary>
    public static IReadOnlyCollection<string> GetMethodNames(Type clazz) =>
        GetDescriptor(clazz).Methods.Keys.ToImmutableArray();
}
