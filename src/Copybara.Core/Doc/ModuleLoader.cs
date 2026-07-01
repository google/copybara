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

using System.Collections;
using System.Collections.Immutable;
using System.Net;
using System.Reflection;
using System.Text.RegularExpressions;
using Copybara.Doc.Annotations;
using Starlark.Annot;
using static Copybara.Doc.DocBase;
using StarlarkRt = Starlark.Eval.Starlark;

namespace Copybara.Doc;

/// <summary>
/// Gathers Copybara documentation by reflecting over the loaded module types. Port of
/// <c>com.google.copybara.doc.ModuleLoader</c>.
///
/// <para><b>Port note:</b> the Java tool relied on a build-time annotation processor
/// (<c>AnnotationProcessor</c>) that emitted a <c>starlark_class_list.txt</c> file inside each jar,
/// listing the fully-qualified names of all <c>@StarlarkBuiltin</c>/<c>@Library</c> classes; that
/// file was then read out of the jars at doc-generation time. In the .NET port there is no such
/// processor and no proto glue. Instead we discover the module types directly at runtime via
/// <see cref="System.Reflection"/> — either from an explicit list of <see cref="Type"/>s or by
/// scanning the given assemblies for <c>[StarlarkBuiltin]</c>/<c>[Library]</c>-annotated types.
/// This is the pragmatic .NET equivalent of the annotation-processor + class-list-file pipeline.</para>
/// </summary>
public sealed class ModuleLoader
{
    /// <summary>
    /// Loads documentation modules by scanning the given assemblies for annotated types, plus the
    /// explicitly supplied <paramref name="additionalTypes"/>. This is the .NET analogue of Java's
    /// <c>load(List&lt;String&gt; jarFiles, List&lt;String&gt; additionalClasses)</c>: instead of
    /// reading a class-list file out of jars, we reflect over loaded assemblies.
    /// </summary>
    public ImmutableArray<DocModule> Load(
        IEnumerable<Assembly> assemblies, IEnumerable<Type>? additionalTypes = null)
    {
        var types = new List<Type>();
        foreach (Assembly asm in assemblies)
        {
            foreach (Type t in SafeGetTypes(asm))
            {
                if (t.GetCustomAttribute<StarlarkBuiltinAttribute>() != null
                    || t.GetCustomAttribute<LibraryAttribute>() != null
                    || t.GetMethods(BindingFlags.Public | BindingFlags.Instance | BindingFlags.Static)
                        .Any(m => m.GetCustomAttribute<StarlarkMethodAttribute>() != null))
                {
                    types.Add(t);
                }
            }
        }
        if (additionalTypes != null)
        {
            types.AddRange(additionalTypes);
        }
        return LoadTypes(types);
    }

    /// <summary>
    /// Loads documentation from an explicit set of module types (equivalent to Java's resolved class
    /// list). Kept public so tests can drive the extractor deterministically.
    /// </summary>
    public ImmutableArray<DocModule> LoadTypes(IEnumerable<Type> classes)
    {
        var modules = new List<DocModule>();
        var docModule = new DocModule("Globals", "Global functions available in Copybara", true);
        modules.Add(docModule);

        foreach (Type cls in classes.Distinct())
        {
            if (cls.GetCustomAttribute<LibraryAttribute>() != null)
            {
                foreach (DocFunction f in ProcessFunctions(cls, null))
                {
                    docModule.Functions.Add(f);
                }
            }

            StarlarkBuiltinAttribute? starlarkBuiltin = cls.GetCustomAttribute<StarlarkBuiltinAttribute>();
            if (starlarkBuiltin != null)
            {
                if (!starlarkBuiltin.Documented)
                {
                    continue;
                }
                DocSignaturePrefixAttribute? prefixAnn = cls.GetCustomAttribute<DocSignaturePrefixAttribute>();
                string prefix = prefixAnn != null ? prefixAnn.Value : starlarkBuiltin.Name;
                var mod = new DocModule(starlarkBuiltin.Name, starlarkBuiltin.Doc, starlarkBuiltin.Documented);
                foreach (DocFunction f in ProcessFunctions(cls, prefix))
                {
                    mod.Functions.Add(f);
                }
                foreach (DocField field in ProcessFields(cls))
                {
                    mod.Fields.Add(field);
                }
                foreach (DocFlag flag in GenerateFlagsInfo(cls))
                {
                    mod.Flags.Add(flag);
                }
                modules.Add(mod);
                continue;
            }

            // Globals-only library: any [StarlarkMethod]-annotated method contributes to Globals.
            if (GetStarlarkMethods(cls).Any())
            {
                foreach (DocFunction f in ProcessFunctions(cls, null))
                {
                    docModule.Functions.Add(f);
                }
            }
        }

        return DeduplicateAndSort(modules);
    }

    private static IEnumerable<Type> SafeGetTypes(Assembly asm)
    {
        try
        {
            return asm.GetTypes();
        }
        catch (ReflectionTypeLoadException e)
        {
            return e.Types.Where(t => t != null)!.Cast<Type>();
        }
    }

    private static IEnumerable<(MethodInfo Method, StarlarkMethodAttribute Annotation)> GetStarlarkMethods(
        Type cls)
    {
        foreach (MethodInfo m in cls.GetMethods(
            BindingFlags.Public | BindingFlags.Instance | BindingFlags.Static | BindingFlags.FlattenHierarchy))
        {
            var ann = m.GetCustomAttribute<StarlarkMethodAttribute>();
            if (ann != null)
            {
                yield return (m, ann);
            }
        }
    }

    private IEnumerable<DocField> ProcessFields(Type cls)
    {
        return GetStarlarkMethods(cls)
            .Where(e => e.Annotation.StructField)
            .Select(e => ProcessStarlarkMethod(e.Method, e.Annotation, null))
            .Select(m => new DocField(m.Name, m.Description, m.ReturnType, m.IsDocumented))
            .ToImmutableArray();
    }

    private IEnumerable<DocFunction> ProcessFunctions(Type cls, string? prefix)
    {
        var functions = new List<DocFunction>();
        // Java calls Starlark.getSelfCallMethod; here the selfCall method is one annotated with
        // SelfCall = true.
        foreach (var (method, ann) in GetStarlarkMethods(cls))
        {
            if (ann.SelfCall)
            {
                functions.Add(ProcessStarlarkMethod(method, ann, prefix));
            }
        }
        functions.AddRange(
            GetStarlarkMethods(cls)
                .Where(e => !e.Annotation.StructField && !e.Annotation.SelfCall)
                .Select(e => ProcessStarlarkMethod(e.Method, e.Annotation, prefix)));
        return functions;
    }

    private DocFunction ProcessStarlarkMethod(
        MethodInfo method, StarlarkMethodAttribute annotation, string? prefix)
    {
        // In the .NET port, [Param]/[ParamType] annotations live on the C# parameters themselves
        // (not in a nested parameters={} array as in Java). Interpreter-supplied parameters
        // (StarlarkThread / StarlarkSemantics) are matched by type and skipped here.
        ParameterInfo[] clrParams = method.GetParameters();
        var starlarkParams = clrParams
            .Where(p => p.GetCustomAttribute<ParamAttribute>() != null)
            .ToList();

        var docDefaultsMap = new Dictionary<string, DocDefaultAttribute>();
        foreach (DocDefaultAttribute dd in method.GetCustomAttributes<DocDefaultAttribute>())
        {
            docDefaultsMap[dd.Field] = dd;
        }

        var paramsList = new List<DocParam>();
        foreach (ParameterInfo clrParam in starlarkParams)
        {
            ParamAttribute starlarkParam = clrParam.GetCustomAttribute<ParamAttribute>()!;
            Type parameterType = clrParam.ParameterType;

            // Compute allowed type names (e.g. string or bool or NoneType).
            var allowedTypeNames = new List<string>();
            ParamTypeAttribute[] paramTypes = clrParam.GetCustomAttributes<ParamTypeAttribute>().ToArray();
            if (starlarkParam.AllowedTypes.Length > 0)
            {
                foreach (Type t in starlarkParam.AllowedTypes)
                {
                    allowedTypeNames.Add(SkylarkTypeName(t));
                }
            }
            else if (paramTypes.Length > 0)
            {
                foreach (ParamTypeAttribute pt in paramTypes)
                {
                    allowedTypeNames.Add(
                        SkylarkTypeName(pt.Type)
                            + (pt.Generic1 != null && pt.Generic1 != typeof(object)
                                ? " of " + SkylarkTypeName(pt.Generic1)
                                : ""));
                }
            }
            else
            {
                allowedTypeNames.Add(SkylarkTypeName(parameterType));
            }

            string paramName = string.IsNullOrEmpty(starlarkParam.Name) ? clrParam.Name ?? "" : starlarkParam.Name;

            docDefaultsMap.TryGetValue(paramName, out DocDefaultAttribute? fieldInfo);
            if (fieldInfo != null && fieldInfo.AllowedTypes.Length > 0)
            {
                allowedTypeNames = fieldInfo.AllowedTypes.ToList();
            }
            paramsList.Add(
                new DocParam(
                    paramName,
                    fieldInfo != null ? fieldInfo.Value : EmptyToNull(starlarkParam.DefaultValue),
                    allowedTypeNames,
                    starlarkParam.Doc,
                    starlarkParam.Documented));
        }

        // Java handles extraKeywords()/extraPositionals() named metadata. The .NET StarlarkMethod
        // attribute has no such fields; residual *args/**kwargs are inferred structurally by the
        // interpreter (see MethodDescriptor), not documented via metadata here.
        // TODO(port): surface extraPositionals/extraKeywords docs if/when the attribute gains them.
        bool hasStar = false;
        bool hasStarStar = false;

        Type returnType = method.ReturnType;
        string? returnTypeName =
            returnType == typeof(void) || StarlarkRt.ClassType(returnType) == "NoneType"
                ? null
                : SkylarkTypeName(returnType);

        string name = prefix != null
            ? prefix + (annotation.SelfCall ? "" : "." + annotation.Name)
            : annotation.Name;

        var examples = method.GetCustomAttributes<ExampleAttribute>().Select(e => new DocExample(e));

        return new DocFunction(
            name,
            annotation.Doc,
            returnTypeName,
            paramsList,
            GenerateFlagsInfo(method),
            examples,
            hasStar,
            hasStarStar,
            annotation.SelfCall,
            annotation.Documented);
    }

    private IEnumerable<DocFlag> GenerateFlagsInfo(MemberInfo el)
    {
        var result = new List<DocFlag>();
        var usesFlags = el.GetCustomAttribute<UsesFlagsAttribute>();
        if (usesFlags == null)
        {
            return result;
        }
        foreach (Type c in usesFlags.Value)
        {
            foreach (MemberInfo m in
                c.GetMembers(BindingFlags.Public | BindingFlags.NonPublic | BindingFlags.Instance | BindingFlags.Static))
            {
                if (m is not (PropertyInfo or FieldInfo))
                {
                    continue;
                }
                var flag = m.GetCustomAttribute<FlagAttribute>();
                if (flag == null || flag.Hidden)
                {
                    continue;
                }
                Type memberType = m is PropertyInfo pi ? pi.PropertyType : ((FieldInfo)m).FieldType;
                string description = flag.Description;
                if (memberType == typeof(TimeSpan))
                {
                    // Java appended a note for DurationConverter-backed flags.
                    description += (description.EndsWith(".", StringComparison.Ordinal) ? " " : ". ")
                        + " Example values: 30s, 20m, 1h, etc.";
                }
                result.Add(
                    new DocFlag(
                        string.Join(", ", flag.Names),
                        SimplerJavaTypes(memberType),
                        description,
                        !flag.Hidden));
            }
        }
        return result;
    }

    private static readonly Regex TypeNameRegex = new("(?:[A-Za-z.]*\\.)*([A-Za-z]+)");

    private string SimplerJavaTypes(Type s)
    {
        Type underlying = Nullable.GetUnderlyingType(s) ?? s;
        if (underlying.IsEnum)
        {
            return "`" + string.Join("`<br>or `", Enum.GetNames(underlying)) + "`";
        }
        string result = TypeNameRegex.Replace(underlying.Name, m => DeCapitalize(m.Groups[1].Value));
        return WebUtility.HtmlEncode(result);
    }

    private static string DeCapitalize(string substring) =>
        substring.Length == 0
            ? substring
            : char.ToLowerInvariant(substring[0]) + substring.Substring(1);

    /// <summary>
    /// Best-effort Starlark type name for a CLR type. Simplified relative to Java's generic
    /// reflection (which walked ParameterizedType / WildcardType / TypeVariable); .NET generics are
    /// handled here for the common dict/sequence collection shapes.
    /// </summary>
    private string SkylarkTypeName(Type type)
    {
        Type t = Nullable.GetUnderlyingType(type) ?? type;

        if (t.IsGenericType)
        {
            Type def = t.GetGenericTypeDefinition();
            Type[] args = t.GetGenericArguments();

            if (typeof(IDictionary).IsAssignableFrom(t)
                || def == typeof(IDictionary<,>)
                || def == typeof(IReadOnlyDictionary<,>)
                || def == typeof(Dictionary<,>))
            {
                if (args.Length == 2)
                {
                    return IsObject(args[0]) || IsObject(args[1])
                        ? "dict"
                        : $"dict[{SkylarkTypeName(args[0])}, {SkylarkTypeName(args[1])}]";
                }
            }

            if (args.Length == 1
                && typeof(IEnumerable).IsAssignableFrom(t)
                && t != typeof(string))
            {
                return IsObject(args[0]) ? "sequence" : $"list of {SkylarkTypeName(args[0])}";
            }

            return StarlarkRt.ClassType(t);
        }

        if (t.IsGenericParameter)
        {
            return "?";
        }

        return StarlarkRt.ClassType(t);
    }

    private static bool IsObject(Type type) => type == typeof(object);

    private static string? EmptyToNull(string? value) => string.IsNullOrEmpty(value) ? null : value;

    private static ImmutableArray<DocModule> DeduplicateAndSort(IEnumerable<DocModule> modules)
    {
        var asMap = new SortedDictionary<string, DocModule>(StringComparer.OrdinalIgnoreCase);
        foreach (DocModule module in modules)
        {
            if (!asMap.TryGetValue(module.Name, out DocModule? existing)
                || existing.Functions.Count < module.Functions.Count
                || existing.Fields.Count < module.Fields.Count
                || existing.Flags.Count < module.Flags.Count)
            {
                asMap[module.Name] = module;
            }
        }
        return asMap.Values.ToImmutableArray();
    }
}
