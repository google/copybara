// Copyright 2025 The Bazel Authors. All rights reserved.
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
using System.Linq;

namespace Starlark.Syntax;

/// <summary>
/// Definitions of types.
///
/// <para><code>
///   t1, t2 ::= None | bool | int | float | str | object
///           | t1|t2 | list[t1]
/// </code></para>
/// </summary>
public static class Types
{
    /// <summary>
    /// The Dynamic type of gradual typing; compatible with any other type, but not related by
    /// subtyping to any other type.
    /// </summary>
    public static readonly StarlarkType ANY = new AnyType();

    /// <summary>The top type of the type hierarchy.</summary>
    public static readonly StarlarkType OBJECT = new ObjectType();

    /// <summary>The bottom type of the type hierarchy.</summary>
    public static readonly StarlarkType NEVER = new NeverType();

    // Primitive types
    public static readonly StarlarkType NONE = new NoneType();

    public static readonly StarlarkType BOOL = new BoolType();
    public static readonly StarlarkType INT = new IntType();
    public static readonly StarlarkType FLOAT = new FloatType();
    public static readonly StarlarkType STR = new StrType();

    // A frequently-used union `int | float`.
    public static readonly UnionType NUMERIC = (UnionType)Union(INT, FLOAT);

    // A frequently-used empty tuple type.
    public static readonly FixedLengthTupleType EMPTY_TUPLE = Tuple(ImmutableArray<StarlarkType>.Empty);

    // A frequently used function without parameters, that returns Any.
    public static readonly CallableType NO_PARAMS_CALLABLE =
        Callable(
            ImmutableArray<string>.Empty,
            ImmutableArray<StarlarkType>.Empty,
            0,
            0,
            ImmutableHashSet<string>.Empty,
            null,
            null,
            ANY);

    public static readonly TypeConstructor ANY_CONSTRUCTOR = WrapType("Any", ANY);
    public static readonly TypeConstructor OBJECT_CONSTRUCTOR = WrapType("object", OBJECT);
    public static readonly TypeConstructor NONE_CONSTRUCTOR = WrapType("None", NONE);
    public static readonly TypeConstructor BOOL_CONSTRUCTOR = WrapType("bool", BOOL);
    public static readonly TypeConstructor INT_CONSTRUCTOR = WrapType("int", INT);
    public static readonly TypeConstructor FLOAT_CONSTRUCTOR = WrapType("float", FLOAT);
    public static readonly TypeConstructor STR_CONSTRUCTOR = WrapType("str", STR);
    public static readonly TypeConstructor LIST_CONSTRUCTOR = WrapTypeConstructor("list", List);
    public static readonly TypeConstructor DICT_CONSTRUCTOR = WrapTypeConstructor("dict", Dict);
    public static readonly TypeConstructor SET_CONSTRUCTOR = WrapTypeConstructor("set", Set);
    public static readonly TypeConstructor TUPLE_CONSTRUCTOR = WrapTupleConstructor();

    public static readonly TypeConstructor COLLECTION_CONSTRUCTOR =
        WrapTypeConstructor("Collection", Collection);

    public static readonly TypeConstructor SEQUENCE_CONSTRUCTOR =
        WrapTypeConstructor("Sequence", Sequence);

    public static readonly TypeConstructor MAPPING_CONSTRUCTOR =
        WrapTypeConstructor("Mapping", Mapping);

    public static readonly ImmutableDictionary<string, TypeConstructor> TYPE_UNIVERSE =
        MakeTypeUniverse();

    private static ImmutableDictionary<string, TypeConstructor> MakeTypeUniverse()
    {
        var env = ImmutableDictionary.CreateBuilder<string, TypeConstructor>();
        env.Add("Any", ANY_CONSTRUCTOR);
        env.Add("object", OBJECT_CONSTRUCTOR);
        env.Add("None", NONE_CONSTRUCTOR);
        env.Add("bool", BOOL_CONSTRUCTOR);
        env.Add("int", INT_CONSTRUCTOR);
        env.Add("float", FLOAT_CONSTRUCTOR);
        env.Add("str", STR_CONSTRUCTOR);
        env.Add("list", LIST_CONSTRUCTOR);
        env.Add("dict", DICT_CONSTRUCTOR);
        env.Add("set", SET_CONSTRUCTOR);
        env.Add("tuple", TUPLE_CONSTRUCTOR);
        env.Add("Collection", COLLECTION_CONSTRUCTOR);
        env.Add("Sequence", SEQUENCE_CONSTRUCTOR);
        env.Add("Mapping", MAPPING_CONSTRUCTOR);
        return env.ToImmutable();
    }

    // ==== Singleton primitive types ====

    private sealed class AnyType : StarlarkType
    {
        public override string ToString() => "Any";

        public override int GetHashCode() => typeof(AnyType).GetHashCode();

        public override bool Equals(object? obj) => obj is AnyType;

        public override StarlarkType? GetField(string name, TypeContext context) => ANY;

        internal override StarlarkType? InferBinaryOperator(TokenKind op, StarlarkType that, bool thisLeft)
        {
            return op switch
            {
                // If we are the LHS, fall through to RHS's inferBinaryOperator; RHS determines
                // whether it is membership-testable.
                // If we are the RHS, act as a membership-testable type that allows any LHS (e.g.
                // list) and return bool.
                TokenKind.IN or TokenKind.NOT_IN => thisLeft ? null : BOOL,
                _ => ANY,
            };
        }

        protected internal override bool IsComparable(StarlarkType that)
        {
            // Instead of enumerating all comparable types here, allow StarlarkType.comparable to
            // defer to that.isComparable(ANY).
            return that.Equals(ANY);
        }

        public override bool HasSetIndex() => true;

        public override bool HasSetField() => true;
    }

    private sealed class ObjectType : StarlarkType
    {
        public override string ToString() => "object";

        public override int GetHashCode() => typeof(ObjectType).GetHashCode();

        public override bool Equals(object? obj) => obj is ObjectType;
    }

    private sealed class NeverType : StarlarkType
    {
        public override string ToString() => "Never";

        public override int GetHashCode() => typeof(NeverType).GetHashCode();

        public override bool Equals(object? obj) => obj is NeverType;

        protected internal override bool IsComparable(StarlarkType that)
        {
            // Regard Never - as the bottom type - to be comparable to anything; in particular, this
            // allows empty lists (i.e. list[Never]) to be comparable to arbitrary non-empty lists.
            return true;
        }

        public override bool HasSetIndex() => true;

        public override bool HasSetField() => true;
    }

    private sealed class NoneType : StarlarkType
    {
        public override string ToString() => "None";

        public override int GetHashCode() => typeof(NoneType).GetHashCode();

        public override bool Equals(object? obj) => obj is NoneType;
    }

    private sealed class BoolType : StarlarkType
    {
        public override string ToString() => "bool";

        public override int GetHashCode() => typeof(BoolType).GetHashCode();

        public override bool Equals(object? obj) => obj is BoolType;

        protected internal override bool IsComparable(StarlarkType that)
        {
            return StarlarkType.AssignableFrom(BOOL, that);
        }
    }

    private sealed class IntType : StarlarkType
    {
        public override string ToString() => "int";

        public override int GetHashCode() => typeof(IntType).GetHashCode();

        public override bool Equals(object? obj) => obj is IntType;

        internal override StarlarkType? InferBinaryOperator(TokenKind op, StarlarkType that, bool thisLeft)
        {
            return op switch
            {
                TokenKind.PLUS or TokenKind.MINUS or TokenKind.PERCENT or TokenKind.SLASH_SLASH =>
                    NUMERIC.GetTypes().Contains(that) ? that : null,
                TokenKind.SLASH => NUMERIC.GetTypes().Contains(that) ? FLOAT : null,
                // Repetition operator (int * str, int * list, etc.) is assumed to be symmetric and
                // implemented by the rhs, so defer to rhs for non-numeric case.
                TokenKind.STAR => NUMERIC.GetTypes().Contains(that) ? that : null,
                TokenKind.AMPERSAND or TokenKind.CARET or TokenKind.GREATER_GREATER
                    or TokenKind.LESS_LESS or TokenKind.PIPE => that.Equals(INT) ? INT : null,
                _ => null,
            };
        }

        protected internal override bool IsComparable(StarlarkType that)
        {
            return StarlarkType.AssignableFrom(NUMERIC, that);
        }
    }

    private sealed class FloatType : StarlarkType
    {
        public override string ToString() => "float";

        public override int GetHashCode() => typeof(FloatType).GetHashCode();

        public override bool Equals(object? obj) => obj is FloatType;

        internal override StarlarkType? InferBinaryOperator(TokenKind op, StarlarkType that, bool thisLeft)
        {
            return op switch
            {
                TokenKind.PLUS or TokenKind.MINUS or TokenKind.PERCENT or TokenKind.SLASH
                    or TokenKind.SLASH_SLASH or TokenKind.STAR =>
                    NUMERIC.GetTypes().Contains(that) ? FLOAT : null,
                _ => null,
            };
        }

        protected internal override bool IsComparable(StarlarkType that)
        {
            return StarlarkType.AssignableFrom(NUMERIC, that);
        }
    }

    private sealed class StrType : StarlarkType
    {
        public override string ToString() => "str";

        public override int GetHashCode() => typeof(StrType).GetHashCode();

        public override bool Equals(object? obj) => obj is StrType;

        internal override StarlarkType? InferBinaryOperator(TokenKind op, StarlarkType that, bool thisLeft)
        {
            return op switch
            {
                TokenKind.PLUS => that.Equals(STR) ? STR : null,
                // String substitution allows anything on the RHS
                TokenKind.PERCENT => thisLeft ? STR : null,
                TokenKind.STAR => that.Equals(INT) ? STR : null,
                // If we are LHS, defer to the RHS.
                // If we are RHS, explicitly handle Any since AnyType.inferBinaryOperator defers to us.
                TokenKind.IN or TokenKind.NOT_IN =>
                    !thisLeft && (that.Equals(STR) || that.Equals(ANY)) ? BOOL : null,
                _ => null,
            };
        }

        protected internal override bool IsComparable(StarlarkType that)
        {
            return that.Equals(STR) || that.Equals(ANY);
        }
    }

    // ==== Callable ====

    /// <summary>Construct a CallableType representing a Starlark Function.</summary>
    public static CallableType Callable(
        IReadOnlyList<string> parameterNames,
        IReadOnlyList<StarlarkType> parameterTypes,
        int numPositionalOnlyParameters,
        int numPositionalParameters,
        ImmutableHashSet<string> mandatoryParams,
        StarlarkType? varargsType,
        StarlarkType? kwargsType,
        StarlarkType returns)
    {
        if (parameterNames.Count != parameterTypes.Count)
        {
            throw new ArgumentException(
                string.Format("{0} != {1}", parameterNames.Count, parameterTypes.Count));
        }

        return new GeneralCallableType(
            parameterNames.ToImmutableArray(),
            parameterTypes.ToImmutableArray(),
            numPositionalOnlyParameters,
            numPositionalParameters,
            mandatoryParams,
            varargsType,
            kwargsType,
            returns);
    }

    /// <summary>
    /// An interface for the general Starlark callable.
    ///
    /// <para>There are 3 flavours of parameters: positional-only, ordinary, and keyword-only.</para>
    /// </summary>
    public abstract class CallableType : StarlarkType
    {
        public abstract IReadOnlyList<string> GetParameterNames();

        public abstract IReadOnlyList<StarlarkType> GetParameterTypes();

        public abstract int GetNumPositionalOnlyParameters();

        public abstract int GetNumPositionalParameters();

        public abstract ImmutableHashSet<string> GetMandatoryParameters();

        public abstract StarlarkType? GetVarargsType();

        public abstract StarlarkType? GetKwargsType();

        public abstract StarlarkType GetReturnType();

        public StarlarkType GetParameterTypeByPos(int i)
        {
            return GetParameterTypes()[i];
        }

        public override string ToString()
        {
            // Approximate representation of the type - as much as Callable can do
            return "Callable[["
                + string.Join(", ", GetParameterTypes().Select(t => t.ToString()))
                + "], "
                + GetReturnType()
                + "]";
        }

        /// <summary>Returns a complete string representation of the type.</summary>
        public string ToSignatureString()
        {
            var paramsList = new List<string>();

            // positional parameters
            int i = 0;
            for (; i < GetNumPositionalOnlyParameters(); i++)
            {
                string name = GetParameterNames()[i];
                StarlarkType type = GetParameterTypeByPos(i);
                if (GetMandatoryParameters().Contains(name))
                {
                    paramsList.Add(type.ToString()!);
                }
                else
                {
                    paramsList.Add("[" + type + "]");
                }
            }

            if (i > 0)
            {
                // if there were positional-only parameters, we need to separate them
                paramsList.Add("/");
            }

            for (; i < GetNumPositionalParameters(); i++)
            {
                string name = GetParameterNames()[i];
                StarlarkType type = GetParameterTypeByPos(i);
                if (GetMandatoryParameters().Contains(name))
                {
                    paramsList.Add(name + ": " + type);
                }
                else
                {
                    paramsList.Add(name + ": [" + type + "]");
                }
            }

            if (GetVarargsType() != null)
            {
                paramsList.Add("*args: " + GetVarargsType());
            }
            else if (i < GetParameterTypes().Count)
            {
                // if there are going to be kwonly params
                paramsList.Add("*");
            }

            // keyword parameters
            for (; i < GetParameterTypes().Count; i++)
            {
                string name = GetParameterNames()[i];
                string type = GetParameterTypeByPos(i).ToString()!;
                if (GetMandatoryParameters().Contains(name))
                {
                    paramsList.Add(name + ": " + type);
                }
                else
                {
                    paramsList.Add(name + ": [" + type + "]");
                }
            }

            if (GetKwargsType() != null)
            {
                paramsList.Add("**kwargs: " + GetKwargsType());
            }

            return "(" + string.Join(", ", paramsList) + ") -> " + GetReturnType();
        }
    }

    private sealed class GeneralCallableType : CallableType
    {
        private readonly ImmutableArray<string> parameterNames;
        private readonly ImmutableArray<StarlarkType> parameterTypes;
        private readonly int numPositionalOnlyParameters;
        private readonly int numPositionalParameters;
        private readonly ImmutableHashSet<string> mandatoryParameters;
        private readonly StarlarkType? varargsType;
        private readonly StarlarkType? kwargsType;
        private readonly StarlarkType returnType;

        internal GeneralCallableType(
            ImmutableArray<string> parameterNames,
            ImmutableArray<StarlarkType> parameterTypes,
            int numPositionalOnlyParameters,
            int numPositionalParameters,
            ImmutableHashSet<string> mandatoryParameters,
            StarlarkType? varargsType,
            StarlarkType? kwargsType,
            StarlarkType returnType)
        {
            this.parameterNames = parameterNames;
            this.parameterTypes = parameterTypes;
            this.numPositionalOnlyParameters = numPositionalOnlyParameters;
            this.numPositionalParameters = numPositionalParameters;
            this.mandatoryParameters = mandatoryParameters;
            this.varargsType = varargsType;
            this.kwargsType = kwargsType;
            this.returnType = returnType;
        }

        public override IReadOnlyList<string> GetParameterNames() => parameterNames;

        public override IReadOnlyList<StarlarkType> GetParameterTypes() => parameterTypes;

        public override int GetNumPositionalOnlyParameters() => numPositionalOnlyParameters;

        public override int GetNumPositionalParameters() => numPositionalParameters;

        public override ImmutableHashSet<string> GetMandatoryParameters() => mandatoryParameters;

        public override StarlarkType? GetVarargsType() => varargsType;

        public override StarlarkType? GetKwargsType() => kwargsType;

        public override StarlarkType GetReturnType() => returnType;

        public override bool Equals(object? obj)
        {
            if (obj is not GeneralCallableType other)
            {
                return false;
            }

            return parameterNames.SequenceEqual(other.parameterNames)
                && parameterTypes.SequenceEqual(other.parameterTypes)
                && numPositionalOnlyParameters == other.numPositionalOnlyParameters
                && numPositionalParameters == other.numPositionalParameters
                && mandatoryParameters.SetEquals(other.mandatoryParameters)
                && Nullable.Equals(varargsType, other.varargsType)
                && Nullable.Equals(kwargsType, other.kwargsType)
                && returnType.Equals(other.returnType);
        }

        public override int GetHashCode()
        {
            var hash = default(HashCode);
            foreach (string n in parameterNames)
            {
                hash.Add(n);
            }
            foreach (StarlarkType t in parameterTypes)
            {
                hash.Add(t);
            }
            hash.Add(numPositionalOnlyParameters);
            hash.Add(numPositionalParameters);
            hash.Add(returnType);
            hash.Add(varargsType);
            hash.Add(kwargsType);
            return hash.ToHashCode();
        }
    }

    // ==== Union ====

    /// <summary>
    /// Constructs a union type.
    ///
    /// <para>If the types set contains another Union type it's flattened. Duplicates are removed.
    /// Occurrences of Never are removed.</para>
    ///
    /// <para>If types set contains Object type it's simplified to Object type. If the set contains a
    /// single element, it is returned instead of constructing a union. And if the set is empty,
    /// Never is returned.</para>
    /// </summary>
    public static StarlarkType Union(params StarlarkType[] types)
    {
        return Union(ImmutableHashSet.CreateRange(types));
    }

    /// <summary>Constructs a union type.</summary>
    public static StarlarkType Union(ImmutableHashSet<StarlarkType> types)
    {
        var subtypesBuilder = ImmutableHashSet.CreateBuilder<StarlarkType>();
        // Unions are flattened
        foreach (StarlarkType type in types)
        {
            if (type is UnionType union)
            {
                subtypesBuilder.UnionWith(union.GetTypes());
            }
            else if (!type.Equals(NEVER))
            {
                subtypesBuilder.Add(type);
            }
        }

        ImmutableHashSet<StarlarkType> subtypes = subtypesBuilder.ToImmutable();
        if (subtypes.Contains(OBJECT))
        {
            return OBJECT;
        }
        if (subtypes.Count == 1)
        {
            return subtypes.First();
        }
        else if (subtypes.Count == 0)
        {
            return NEVER;
        }
        return new UnionType(subtypes);
    }

    public static StarlarkType Union(IReadOnlyList<StarlarkType> types)
    {
        if (types.Count == 1)
        {
            // Optimize the common case.
            return types[0];
        }
        return Union(ImmutableHashSet.CreateRange(types));
    }

    /// <summary>
    /// Returns the list of a union's types, or a singleton list if <paramref name="type"/> is not a
    /// union.
    /// </summary>
    public static IReadOnlyCollection<StarlarkType> UnfoldUnion(StarlarkType type)
    {
        if (type is UnionType unionType)
        {
            return unionType.GetTypes();
        }
        return ImmutableArray.Create(type);
    }

    /// <summary>
    /// Union type.
    ///
    /// <para>Unions must contain at least two types, none of which may be Never or Object. See
    /// <see cref="Union(ImmutableHashSet{StarlarkType})"/>.</para>
    /// </summary>
    public sealed class UnionType : StarlarkType
    {
        private readonly ImmutableHashSet<StarlarkType> types;

        internal UnionType(ImmutableHashSet<StarlarkType> types)
        {
            this.types = types;
        }

        public ImmutableHashSet<StarlarkType> GetTypes() => types;

        public override string ToString()
        {
            return string.Join("|", types.Select(t => t.ToString()));
        }

        protected internal override bool IsComparable(StarlarkType that)
        {
            return types.All(type => StarlarkType.Comparable(type, that));
        }

        public override StarlarkType? GetField(string name, TypeContext context)
        {
            var resultTypes = new List<StarlarkType>(types.Count);
            foreach (StarlarkType type in types)
            {
                StarlarkType? result = type.GetField(name, context);
                if (result == null)
                {
                    return null;
                }
                resultTypes.Add(result);
            }
            return Union(resultTypes);
        }

        public override bool HasSetIndex() => types.All(t => t.HasSetIndex());

        public override bool HasSetField() => types.All(t => t.HasSetField());

        public override bool Equals(object? obj)
        {
            return obj is UnionType other && types.SetEquals(other.types);
        }

        public override int GetHashCode()
        {
            // Order-independent hash.
            int h = 0;
            foreach (StarlarkType t in types)
            {
                h ^= t.GetHashCode();
            }
            return h;
        }
    }

    // ==== List ====

    public static ListType List(StarlarkType elementType)
    {
        return new ListType(elementType);
    }

    /// <summary>List type.</summary>
    public sealed class ListType : AbstractSequenceType
    {
        private readonly StarlarkType elementType;

        internal ListType(StarlarkType elementType)
        {
            this.elementType = elementType;
        }

        public override StarlarkType GetElementType() => elementType;

        public override IReadOnlyList<StarlarkType> GetSupertypes()
        {
            return ImmutableArray.Create<StarlarkType>(
                Sequence(GetElementType()), Collection(GetElementType()));
        }

        public override string ToString() => "list[" + GetElementType() + "]";

        internal override StarlarkType? InferBinaryOperator(TokenKind op, StarlarkType that, bool thisLeft)
        {
            return op switch
            {
                TokenKind.PLUS => that is ListType thatList
                    ? List(Union(GetElementType(), thatList.GetElementType()))
                    : null,
                TokenKind.STAR => that.Equals(INT) ? this : null,
                _ => base.InferBinaryOperator(op, that, thisLeft),
            };
        }

        public override StarlarkType? GetField(string name, TypeContext context)
        {
            return context.GetListFieldType(name);
        }

        protected internal override bool IsComparable(StarlarkType that)
        {
            if (that.Equals(ANY))
            {
                return true;
            }
            else if (that is ListType thatList)
            {
                return Comparable(GetElementType(), thatList.GetElementType());
            }
            return false;
        }

        public override bool HasSetIndex() => true;

        public override bool Equals(object? obj)
        {
            return obj is ListType other && elementType.Equals(other.elementType);
        }

        public override int GetHashCode() => HashCode.Combine(typeof(ListType), elementType);
    }

    // ==== Dict ====

    public static DictType Dict(StarlarkType keyType, StarlarkType valueType)
    {
        return new DictType(keyType, valueType);
    }

    /// <summary>Dict type.</summary>
    public sealed class DictType : AbstractMappingType
    {
        private readonly StarlarkType keyType;
        private readonly StarlarkType valueType;

        internal DictType(StarlarkType keyType, StarlarkType valueType)
        {
            this.keyType = keyType;
            this.valueType = valueType;
        }

        public override StarlarkType GetKeyType() => keyType;

        public override StarlarkType GetValueType() => valueType;

        public override IReadOnlyList<StarlarkType> GetSupertypes()
        {
            return ImmutableArray.Create<StarlarkType>(
                Collection(GetKeyType()), Mapping(GetKeyType(), GetValueType()));
        }

        public override string ToString() => "dict[" + GetKeyType() + ", " + GetValueType() + "]";

        public override StarlarkType? GetField(string name, TypeContext context)
        {
            return context.GetDictFieldType(name);
        }

        public override bool HasSetIndex() => true;

        public override bool Equals(object? obj)
        {
            return obj is DictType other
                && keyType.Equals(other.keyType)
                && valueType.Equals(other.valueType);
        }

        public override int GetHashCode() => HashCode.Combine(typeof(DictType), keyType, valueType);
    }

    // ==== Set ====

    public static SetType Set(StarlarkType elementType)
    {
        return new SetType(elementType);
    }

    /// <summary>Set type.</summary>
    public sealed class SetType : AbstractCollectionType
    {
        private readonly StarlarkType elementType;

        internal SetType(StarlarkType elementType)
        {
            this.elementType = elementType;
        }

        public override StarlarkType GetElementType() => elementType;

        public override IReadOnlyList<StarlarkType> GetSupertypes()
        {
            return ImmutableArray.Create<StarlarkType>(Collection(GetElementType()));
        }

        public override string ToString() => "set[" + GetElementType() + "]";

        public override StarlarkType? GetField(string name, TypeContext context)
        {
            return context.GetSetFieldType(name);
        }

        internal override StarlarkType? InferBinaryOperator(TokenKind op, StarlarkType that, bool thisLeft)
        {
            return op switch
            {
                // TODO: #27370 - we may want to tighten the type of a set intersection, but it's
                // non-trivial.
                TokenKind.AMPERSAND or TokenKind.MINUS => that is SetType ? this : null,
                TokenKind.CARET or TokenKind.PIPE => that is SetType thatSet
                    ? Set(Union(GetElementType(), thatSet.GetElementType()))
                    : null,
                _ => base.InferBinaryOperator(op, that, thisLeft),
            };
        }

        public override bool Equals(object? obj)
        {
            return obj is SetType other && elementType.Equals(other.elementType);
        }

        public override int GetHashCode() => HashCode.Combine(typeof(SetType), elementType);
    }

    // ==== Tuple ====

    public static FixedLengthTupleType Tuple(IReadOnlyList<StarlarkType> elementTypes)
    {
        return new FixedLengthTupleType(elementTypes.ToImmutableArray());
    }

    public static FixedLengthTupleType Tuple(StarlarkType first, params StarlarkType[] rest)
    {
        var builder = ImmutableArray.CreateBuilder<StarlarkType>();
        builder.Add(first);
        builder.AddRange(rest);
        return new FixedLengthTupleType(builder.ToImmutable());
    }

    public static HomogeneousTupleType HomogeneousTuple(StarlarkType elementType)
    {
        return new HomogeneousTupleType(elementType);
    }

    /// <summary>Tuple type.</summary>
    public abstract class TupleType : AbstractSequenceType
    {
        /// <summary>Returns the type of this tuple concatenated with another.</summary>
        internal abstract TupleType Concatenate(TupleType rhs);

        /// <summary>Returns the type of this tuple repeated.</summary>
        internal abstract TupleType Repeat(int times);

        /// <summary>Returns the homogeneous version of this tuple type.</summary>
        public abstract HomogeneousTupleType ToHomogeneous();

        internal override StarlarkType? InferBinaryOperator(TokenKind op, StarlarkType that, bool thisLeft)
        {
            return op switch
            {
                TokenKind.PLUS => that is TupleType rhsTuple ? Concatenate(rhsTuple) : null,
                // Special case handled by TypeChecker.inferTupleRepetition.
                TokenKind.STAR => null,
                _ => base.InferBinaryOperator(op, that, thisLeft),
            };
        }
    }

    /// <summary>Tuple type of a fixed length.</summary>
    public sealed class FixedLengthTupleType : TupleType
    {
        private readonly ImmutableArray<StarlarkType> elementTypes;

        internal FixedLengthTupleType(ImmutableArray<StarlarkType> elementTypes)
        {
            this.elementTypes = elementTypes;
        }

        public IReadOnlyList<StarlarkType> GetElementTypes() => elementTypes;

        public override StarlarkType GetElementType() => Union(elementTypes);

        public override IReadOnlyList<StarlarkType> GetSupertypes()
        {
            HomogeneousTupleType homogeneous = ToHomogeneous();
            return ImmutableArray.Create<StarlarkType>(
                homogeneous,
                Sequence(homogeneous.GetElementType()),
                Collection(homogeneous.GetElementType()));
        }

        public override string ToString()
        {
            return string.Format(
                "tuple[{0}]",
                elementTypes.IsEmpty
                    ? "()"
                    : string.Join(", ", elementTypes.Select(t => t.ToString())));
        }

        internal override TupleType Concatenate(TupleType rhs)
        {
            if (rhs is FixedLengthTupleType rhsFixedLength)
            {
                var builder = ImmutableArray.CreateBuilder<StarlarkType>();
                builder.AddRange(elementTypes);
                builder.AddRange(rhsFixedLength.elementTypes);
                return new FixedLengthTupleType(builder.ToImmutable());
            }
            else
            {
                return ToHomogeneous().Concatenate(rhs);
            }
        }

        internal override TupleType Repeat(int times)
        {
            var builder = ImmutableArray.CreateBuilder<StarlarkType>();
            for (int i = 0; i < times; i++)
            {
                builder.AddRange(elementTypes);
            }
            return new FixedLengthTupleType(builder.ToImmutable());
        }

        public override HomogeneousTupleType ToHomogeneous()
        {
            return HomogeneousTuple(Union(elementTypes));
        }

        protected internal override bool IsComparable(StarlarkType that)
        {
            if (that.Equals(ANY))
            {
                return true;
            }
            else if (that is FixedLengthTupleType thatTuple)
            {
                int commonLength = Math.Min(elementTypes.Length, thatTuple.elementTypes.Length);
                for (int i = 0; i < commonLength; i++)
                {
                    if (!Comparable(elementTypes[i], thatTuple.elementTypes[i]))
                    {
                        return false;
                    }
                }
                return true;
            }
            // Comparison with HomogeneousTupleType defers to HomogeneousTupleType.
            return false;
        }

        public override bool Equals(object? obj)
        {
            return obj is FixedLengthTupleType other && elementTypes.SequenceEqual(other.elementTypes);
        }

        public override int GetHashCode()
        {
            var hash = default(HashCode);
            hash.Add(typeof(FixedLengthTupleType));
            foreach (StarlarkType t in elementTypes)
            {
                hash.Add(t);
            }
            return hash.ToHashCode();
        }
    }

    /// <summary>Tuple type of an indeterminate length.</summary>
    public sealed class HomogeneousTupleType : TupleType
    {
        private readonly StarlarkType elementType;

        internal HomogeneousTupleType(StarlarkType elementType)
        {
            this.elementType = elementType;
        }

        public override StarlarkType GetElementType() => elementType;

        public override IReadOnlyList<StarlarkType> GetSupertypes()
        {
            return ImmutableArray.Create<StarlarkType>(
                Sequence(GetElementType()), Collection(GetElementType()));
        }

        public override string ToString() => "tuple[" + GetElementType() + ", ...]";

        internal override TupleType Concatenate(TupleType rhs)
        {
            return rhs is HomogeneousTupleType rhsHomogeneous
                ? HomogeneousTuple(Union(GetElementType(), rhsHomogeneous.GetElementType()))
                : Concatenate(rhs.ToHomogeneous());
        }

        internal override TupleType Repeat(int times)
        {
            return times > 0 ? this : EMPTY_TUPLE;
        }

        public override HomogeneousTupleType ToHomogeneous() => this;

        protected internal override bool IsComparable(StarlarkType that)
        {
            if (that.Equals(ANY))
            {
                return true;
            }
            else if (that is TupleType thatTuple)
            {
                return Comparable(GetElementType(), thatTuple.ToHomogeneous().GetElementType());
            }
            return false;
        }

        public override bool Equals(object? obj)
        {
            return obj is HomogeneousTupleType other && elementType.Equals(other.elementType);
        }

        public override int GetHashCode() => HashCode.Combine(typeof(HomogeneousTupleType), elementType);
    }

    // ==== Collection / Sequence / Mapping ====

    /// <summary>Collection type.</summary>
    public static CollectionType Collection(StarlarkType elementType)
    {
        return new CollectionType(elementType);
    }

    /// <summary>Abstract collection type implementing common functionality. Exists to be subclassed.</summary>
    public abstract class AbstractCollectionType : StarlarkType
    {
        public abstract StarlarkType GetElementType();

        internal override StarlarkType? InferBinaryOperator(TokenKind op, StarlarkType that, bool thisLeft)
        {
            return op switch
            {
                // `in` and `not in` are always valid for collections on the RHS.
                TokenKind.IN or TokenKind.NOT_IN => thisLeft ? null : BOOL,
                _ => null,
            };
        }
    }

    /// <summary>Collection type.</summary>
    public sealed class CollectionType : AbstractCollectionType
    {
        private readonly StarlarkType elementType;

        internal CollectionType(StarlarkType elementType)
        {
            this.elementType = elementType;
        }

        public override StarlarkType GetElementType() => elementType;

        public override string ToString() => "Collection[" + GetElementType() + "]";

        public override bool Equals(object? obj)
        {
            return obj is CollectionType other && elementType.Equals(other.elementType);
        }

        public override int GetHashCode() => HashCode.Combine(typeof(CollectionType), elementType);
    }

    /// <summary>Sequence type.</summary>
    public static SequenceType Sequence(StarlarkType elementType)
    {
        return new SequenceType(elementType);
    }

    /// <summary>Abstract sequence type for common sequence functionality. Exists to be subclassed.</summary>
    public abstract class AbstractSequenceType : AbstractCollectionType
    {
        public override IReadOnlyList<StarlarkType> GetSupertypes()
        {
            return ImmutableArray.Create<StarlarkType>(Collection(GetElementType()));
        }
    }

    /// <summary>Sequence type.</summary>
    public sealed class SequenceType : AbstractSequenceType
    {
        private readonly StarlarkType elementType;

        internal SequenceType(StarlarkType elementType)
        {
            this.elementType = elementType;
        }

        public override StarlarkType GetElementType() => elementType;

        public override string ToString() => "Sequence[" + GetElementType() + "]";

        public override bool Equals(object? obj)
        {
            return obj is SequenceType other && elementType.Equals(other.elementType);
        }

        public override int GetHashCode() => HashCode.Combine(typeof(SequenceType), elementType);
    }

    /// <summary>Mapping type.</summary>
    public static MappingType Mapping(StarlarkType keyType, StarlarkType valueType)
    {
        return new MappingType(keyType, valueType);
    }

    /// <summary>Abstract mapping type for common map functionality. Exists to be subclassed.</summary>
    public abstract class AbstractMappingType : AbstractCollectionType
    {
        public abstract StarlarkType GetKeyType();

        public abstract StarlarkType GetValueType();

        public override IReadOnlyList<StarlarkType> GetSupertypes()
        {
            return ImmutableArray.Create<StarlarkType>(Collection(GetKeyType()));
        }

        public override StarlarkType GetElementType() => GetKeyType();

        internal override StarlarkType? InferBinaryOperator(TokenKind op, StarlarkType rhs, bool thisLeft)
        {
            return op switch
            {
                TokenKind.PIPE => rhs is AbstractMappingType rhsMapping
                    ? Dict(
                        Union(GetKeyType(), rhsMapping.GetKeyType()),
                        Union(GetValueType(), rhsMapping.GetValueType()))
                    : null,
                _ => base.InferBinaryOperator(op, rhs, thisLeft),
            };
        }
    }

    /// <summary>Mapping type.</summary>
    public sealed class MappingType : AbstractMappingType
    {
        private readonly StarlarkType keyType;
        private readonly StarlarkType valueType;

        internal MappingType(StarlarkType keyType, StarlarkType valueType)
        {
            this.keyType = keyType;
            this.valueType = valueType;
        }

        public override StarlarkType GetKeyType() => keyType;

        public override StarlarkType GetValueType() => valueType;

        public override string ToString() => "Mapping[" + GetKeyType() + ", " + GetValueType() + "]";

        public override bool Equals(object? obj)
        {
            return obj is MappingType other
                && keyType.Equals(other.keyType)
                && valueType.Equals(other.valueType);
        }

        public override int GetHashCode() => HashCode.Combine(typeof(MappingType), keyType, valueType);
    }

    // ==== Type constructor factories ====

    internal static TypeConstructor WrapType(string name, StarlarkType type)
    {
        return new DelegatingTypeConstructor(argsTuple =>
        {
            if (argsTuple.Count != 0)
            {
                throw new TypeConstructor.Failure(
                    string.Format("'{0}' does not accept arguments", name));
            }
            return type;
        });
    }

    private static IReadOnlyList<StarlarkType> ToStarlarkTypes(
        string name, IReadOnlyList<TypeConstructor.Arg> args)
    {
        foreach (TypeConstructor.Arg arg in args)
        {
            if (arg is not StarlarkType)
            {
                throw new TypeConstructor.Failure(
                    string.Format("in application to {0}, got '{1}', expected a type", name, arg));
            }
        }
        return args.Cast<StarlarkType>().ToList();
    }

    /// <summary>
    /// Returns a new type constructor wrapping the given one-argument type factory.
    /// </summary>
    internal static TypeConstructor WrapTypeConstructor(
        string name, Func<StarlarkType, StarlarkType> factory)
    {
        return new DelegatingTypeConstructor(args =>
        {
            var types = ToStarlarkTypes(name, args);
            return types.Count switch
            {
                0 => factory(ANY),
                1 => factory(types[0]),
                _ => throw new TypeConstructor.Failure(
                    string.Format("{0}[] accepts exactly 1 argument but got {1}", name, types.Count)),
            };
        });
    }

    /// <summary>
    /// Returns a new type constructor wrapping the given two-argument type factory.
    /// </summary>
    internal static TypeConstructor WrapTypeConstructor(
        string name, Func<StarlarkType, StarlarkType, StarlarkType> factory)
    {
        return new DelegatingTypeConstructor(args =>
        {
            var types = ToStarlarkTypes(name, args);
            return types.Count switch
            {
                0 => factory(ANY, ANY),
                2 => factory(types[0], types[1]),
                _ => throw new TypeConstructor.Failure(
                    string.Format("{0}[] accepts exactly 2 arguments but got {1}", name, types.Count)),
            };
        });
    }

    private static TypeConstructor WrapTupleConstructor()
    {
        // This is a function instead of a constant, so that the order of evaluation doesn't depend
        // on the position in the class.
        return new DelegatingTypeConstructor(args =>
        {
            if (args.Count == 0)
            {
                // `tuple` is equivalent to `tuple[Any, ...]`
                return HomogeneousTuple(ANY);
            }
            for (int i = 0; i < args.Count; i++)
            {
                TypeConstructor.Arg arg = args[i];
                if (arg.Equals(TypeConstructor.Arg.ELLIPSIS))
                {
                    if (i == 1 && args.Count == 2)
                    {
                        return HomogeneousTuple((StarlarkType)args[0]);
                    }
                    throw new TypeConstructor.Failure(
                        "in application to tuple, '...' can only appear as the second of exactly 2"
                            + " arguments, where the first argument is a type");
                }
                else if (arg.Equals(TypeConstructor.Arg.EMPTY_TUPLE))
                {
                    if (args.Count == 1)
                    {
                        return EMPTY_TUPLE;
                    }
                    throw new TypeConstructor.Failure(
                        "in application to tuple, '()' can only appear if it is the only argument");
                }
                else if (arg is not StarlarkType)
                {
                    throw new TypeConstructor.Failure(
                        string.Format(
                            "in application to tuple, got '{0}', expected a type", arg));
                }
            }
            return Tuple(args.Cast<StarlarkType>().ToList());
        });
    }

    /// <summary>Adapts a delegate to the <see cref="TypeConstructor"/> interface.</summary>
    private sealed class DelegatingTypeConstructor : TypeConstructor
    {
        private readonly Func<IReadOnlyList<TypeConstructor.Arg>, StarlarkType> factory;

        internal DelegatingTypeConstructor(
            Func<IReadOnlyList<TypeConstructor.Arg>, StarlarkType> factory)
        {
            this.factory = factory;
        }

        public StarlarkType CreateStarlarkType(IReadOnlyList<TypeConstructor.Arg> argsTuple)
        {
            return factory(argsTuple);
        }
    }
}
