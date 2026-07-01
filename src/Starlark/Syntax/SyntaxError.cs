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

namespace Starlark.Syntax;

/// <summary>
/// A SyntaxError represents a static error associated with the syntax, such as a scanner or parse
/// error, a structural problem, or a failure of identifier resolution. It records a description of
/// the error and its location in the syntax.
/// </summary>
public sealed class SyntaxError
{
    private readonly Location location;
    private readonly string message;

    public SyntaxError(Location location, string message)
    {
        this.location = location ?? throw new ArgumentNullException(nameof(location));
        this.message = message ?? throw new ArgumentNullException(nameof(message));
    }

    /// <summary>Returns the location of the error.</summary>
    public Location Location => location;

    /// <summary>Returns a description of the error.</summary>
    public string Message => message;

    /// <summary>Returns a string of the form <c>"foo.star:1:2: oops"</c>.</summary>
    public override string ToString() => location + ": " + message;

    /// <summary>
    /// A SyntaxError.Exception is an exception holding one or more syntax errors.
    ///
    /// <para>SyntaxError.Exception is thrown by operations such as <c>Expression.Parse</c>, which are
    /// "all or nothing". By contrast, <c>StarlarkFile.Parse</c> does not throw an exception; instead,
    /// it records the accumulated scanner, parser, and optionally validation errors within the syntax
    /// tree, so that clients may obtain partial information from a damaged file.</para>
    /// </summary>
    public sealed class Exception : System.Exception
    {
        private readonly ImmutableArray<SyntaxError> errors;

        /// <summary>Construct a SyntaxError from a non-empty list of errors.</summary>
        public Exception(IReadOnlyList<SyntaxError> errors)
        {
            if (errors.Count == 0)
            {
                throw new ArgumentException("no errors");
            }
            this.errors = errors.ToImmutableArray();
        }

        /// <summary>Returns an immutable non-empty list of errors.</summary>
        public IReadOnlyList<SyntaxError> Errors => errors;

        public override string Message
        {
            get
            {
                string first = errors[0].Message;
                if (errors.Length > 1)
                {
                    return string.Format("{0} (+ {1} more)", first, errors.Length - 1);
                }
                return first;
            }
        }
    }
}
