/*
 * Copyright (C) 2016 Google Inc.
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
using Starlark.Syntax;

namespace Copybara.Transform;

/// <summary>
/// A transformation which delegates to some arbitrary transformation and reverses to some arbitrary
/// transformation.
/// </summary>
public sealed class ExplicitReversal : ITransformation
{
    private readonly ITransformation _forward;
    private readonly ITransformation _reverse;

    public ExplicitReversal(ITransformation forward, ITransformation reverse)
    {
        _forward = Preconditions.CheckNotNull(forward);
        _reverse = Preconditions.CheckNotNull(reverse);
    }

    public TransformationStatus Transform(TransformWork work)
    {
        TransformWork newWork = work.InsideExplicitTransform();
        TransformationStatus status = _forward.Transform(newWork);
        work.UpdateFrom(newWork);
        return status;
    }

    /// <summary>Returns the forward transformation, for introspection and config validation.</summary>
    public ITransformation GetForward() => _forward;

    /// <summary>Returns the reverse transformation, for introspection and config validation.</summary>
    public ITransformation GetReverse() => _reverse;

    public ITransformation Reverse() => new ExplicitReversal(_reverse, _forward);

    public string Describe() => _forward.Describe();

    public Location Location() => _forward.Location();

    public override string ToString() => $"ExplicitReversal{{forward={_forward}, reverse={_reverse}}}";
}
