// Copyright 2014 The Bazel Authors. All rights reserved.
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

using System.Text;

namespace Starlark.Syntax;

/// <summary>
/// A Node is a node in a Starlark syntax tree.
///
/// <para>Nodes may be constructed only by the parser.</para>
/// </summary>
public abstract class Node
{
    // The FileLocations table holds the file name and a compressed
    // mapping from token char offsets to Locations.
    // It is shared by all nodes from the same file.
    internal readonly FileLocations Locs;

    internal Node(FileLocations locs)
    {
        Locs = locs ?? throw new ArgumentNullException(nameof(locs));
    }

    /// <summary>
    /// Returns the node's start offset, as a char index (zero-based count of UTF-16 codes) from the
    /// start of the file.
    /// </summary>
    public abstract int GetStartOffset();

    /// <summary>Returns the location of the start of this syntax node.</summary>
    public Location GetStartLocation() => Locs.GetLocation(GetStartOffset());

    /// <summary>Returns the char offset of the source position immediately after this syntax node.</summary>
    public abstract int GetEndOffset();

    /// <summary>Returns the location of the source position immediately after this syntax node.</summary>
    public Location GetEndLocation() => Locs.GetLocation(GetEndOffset());

    /// <summary>
    /// Returns a pretty-printed representation of this syntax tree.
    /// </summary>
    public string PrettyPrint()
    {
        var buf = new StringBuilder();
        new NodePrinter(buf).PrintNode(this);
        return buf.ToString();
    }

    /// <summary>
    /// Print the syntax node in a form useful for debugging.
    /// </summary>
    public override string ToString()
    {
        return PrettyPrint(); // default behavior, overridden in several subclasses
    }

    /// <summary>
    /// Implements the double dispatch by calling into the node specific <c>Visit</c> method of the
    /// <see cref="NodeVisitor"/>.
    /// </summary>
    public abstract void Accept(NodeVisitor visitor);
}
