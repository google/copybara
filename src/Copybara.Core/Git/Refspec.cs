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
using Copybara.Exceptions;

namespace Copybara.Git;

/// <summary>
/// Represents a git refspec. Port of <c>com.google.copybara.git.Refspec</c>.
/// </summary>
public sealed class Refspec
{
    private readonly string _origin;
    private readonly string _destination;
    private readonly bool _allowNoFastForward;

    private Refspec(string origin, string destination, bool allowNoFastForward)
    {
        _origin = origin;
        _destination = destination;
        _allowNoFastForward = allowNoFastForward;
    }

    public string GetOrigin() => _origin;

    public string GetDestination() => _destination;

    public bool IsAllowNoFastForward() => _allowNoFastForward;

    /// <summary>
    /// Converts a reference from the origin to the destination reference using the refspec.
    ///
    /// <para>Note that the <paramref name="originRef"/> should match the origin refspec.</para>
    /// </summary>
    public string Convert(string originRef)
    {
        if (!_origin.Contains('*'))
        {
            Preconditions.CheckArgument(
                originRef == _origin, "originRef=%s origin=%s", originRef, _origin);
            return _destination;
        }
        else
        {
            var origSplit = _origin.Split('*');
            Preconditions.CheckState(origSplit.Length == 2);
            string fromPrefix = origSplit[0];
            string fromSuffix = origSplit[1];
            Preconditions.CheckArgument(
                originRef.StartsWith(fromPrefix, StringComparison.Ordinal)
                    && originRef.EndsWith(fromSuffix, StringComparison.Ordinal),
                "originRef=%s origin=%s",
                originRef,
                _origin);
            string middle =
                originRef.Substring(
                    fromPrefix.Length, originRef.Length - fromSuffix.Length - fromPrefix.Length);

            var destSplit = _destination.Split('*');
            Preconditions.CheckState(destSplit.Length == 2);
            string toPrefix = destSplit[0];
            string toSuffix = destSplit[1];
            return toPrefix + middle + toSuffix;
        }
    }

    /// <summary>
    /// Tests whether a ref matches the origin pattern of the refspec.
    /// </summary>
    public bool MatchesOrigin(string originRef)
    {
        if (!_origin.Contains('*'))
        {
            return originRef == _origin;
        }
        else
        {
            var origSplit = _origin.Split('*');
            Preconditions.CheckState(origSplit.Length == 2);
            string fromPrefix = origSplit[0];
            string fromSuffix = origSplit[1];
            return originRef.StartsWith(fromPrefix, StringComparison.Ordinal)
                && originRef.EndsWith(fromSuffix, StringComparison.Ordinal);
        }
    }

    public Refspec WithAllowNoFastForward() =>
        new(_origin, _destination, allowNoFastForward: true);

    public Refspec OriginToOrigin() => new(_origin, _origin, _allowNoFastForward);

    public Refspec DestinationToDestination() =>
        new(_destination, _destination, _allowNoFastForward);

    public Refspec Invert() => new(_destination, _origin, _allowNoFastForward);

    /// <summary>Same as <see cref="Create"/>, but does not provide Location data.</summary>
    public static Refspec CreateBuiltin(GitEnvironment gitEnv, string cwd, string refspecParam) =>
        Create(gitEnv, cwd, refspecParam);

    public static Refspec Create(GitEnvironment gitEnv, string cwd, string refspecParam)
    {
        if (string.IsNullOrEmpty(refspecParam))
        {
            throw new InvalidRefspecException("Empty refspec is not allowed");
        }
        bool allowNoFastForward = false;
        string refspecStr = refspecParam;
        if (refspecStr.StartsWith('+'))
        {
            allowNoFastForward = true;
            refspecStr = refspecStr.Substring(1);
        }
        var elements = refspecStr.Split(':');
        if (elements.Length > 2)
        {
            throw new InvalidRefspecException(
                "Invalid refspec. Multiple ':' found: '" + refspecParam);
        }
        string origin = elements[0];
        string destination = origin;
        GitRepository.ValidateRefSpec(gitEnv, cwd, origin);
        if (elements.Length > 1)
        {
            destination = elements[1];
            GitRepository.ValidateRefSpec(gitEnv, cwd, destination);
        }
        if (origin.Contains('*') != destination.Contains('*'))
        {
            throw new InvalidRefspecException(
                "Wildcard only used in one part of the refspec: " + refspecParam);
        }
        return new Refspec(origin, destination, allowNoFastForward);
    }

    public override string ToString() =>
        (_allowNoFastForward ? "+" : "") + _origin + ":" + _destination;
}
