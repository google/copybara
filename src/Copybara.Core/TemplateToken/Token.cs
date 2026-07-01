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

using Copybara.Common;

namespace Copybara.TemplateToken;

/// <summary>
/// Either a string literal or interpolated value.
/// </summary>
public sealed class Token
{
    private readonly string _value;
    private readonly TokenType _type;

    internal Token(string value, TokenType type)
    {
        _value = Preconditions.CheckNotNull(value);
        _type = type;
    }

    /// <summary>Create an interpolation token.</summary>
    public static Token Interpolation(string name) => new(name, TokenType.Interpolation);

    /// <summary>Create a literal token.</summary>
    public static Token Literal(string name) => new(name, TokenType.Literal);

    public string GetValue() => _value;

    public TokenType GetTokenType() => _type;

    public override string ToString() => $"Token{{value={_value}, type={_type}}}";

    public override bool Equals(object? obj) =>
        obj is Token other && _value == other._value && _type == other._type;

    public override int GetHashCode() => HashCode.Combine(_value, _type);
}

/// <summary>The type of the token.</summary>
public enum TokenType
{
    Literal,
    Interpolation,
}
