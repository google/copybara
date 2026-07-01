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

using Copybara.Common;
using Starlark.Annot;
using Starlark.Eval;

namespace Copybara.Revision;

/// <summary>
/// Reference to the change/review read from the origin.
/// </summary>
[StarlarkBuiltin("origin_ref", Doc = "Reference to the change/review in the origin.")]
public class OriginRef : IStarlarkValue
{
    private readonly string _ref;

    public OriginRef(string id)
    {
        _ref = Preconditions.CheckNotNull(id);
    }

    /// <summary>Origin reference.</summary>
    [StarlarkMethod("ref", Doc = "Origin reference ref", StructField = true)]
    public string Ref => _ref;

    public override bool Equals(object? o)
    {
        if (ReferenceEquals(this, o))
        {
            return true;
        }
        if (o is null || GetType() != o.GetType())
        {
            return false;
        }
        var originRef = (OriginRef)o;
        return string.Equals(_ref, originRef._ref);
    }

    public override int GetHashCode() => _ref.GetHashCode();

    public override string ToString() => $"OriginRef{{ref={_ref}}}";
}
