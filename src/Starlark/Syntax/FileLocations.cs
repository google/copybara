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

namespace Starlark.Syntax;

/// <summary>
/// FileLocations maps each source offset within a file to a Location. An offset is a (UTF-16) char
/// index such that <c>0 &lt;= offset &lt;= size</c>. A Location is a (file, line, column) triple.
/// </summary>
internal sealed class FileLocations
{
    private readonly int[] linestart; // maps line number (line >= 1) to char offset
    private readonly string file;
    private readonly int size; // size of file in chars

    private FileLocations(int[] linestart, string file, int size)
    {
        this.linestart = linestart;
        this.file = file;
        this.size = size;
    }

    internal static FileLocations Create(char[] buffer, string file)
    {
        return new FileLocations(ComputeLinestart(buffer), file, buffer.Length);
    }

    internal string File => file;

    private int GetLineAt(int offset)
    {
        if (offset < 0 || offset > size)
        {
            throw new InvalidOperationException("Illegal position: " + offset);
        }
        int lowBoundary = 1;
        int highBoundary = linestart.Length - 1;
        while (true)
        {
            if ((highBoundary - lowBoundary) <= 1)
            {
                if (linestart[highBoundary] > offset)
                {
                    return lowBoundary;
                }
                return highBoundary;
            }
            int medium = lowBoundary + ((highBoundary - lowBoundary) >> 1);
            if (linestart[medium] > offset)
            {
                highBoundary = medium;
            }
            else
            {
                lowBoundary = medium;
            }
        }
    }

    internal Location GetLocation(int offset)
    {
        int line = GetLineAt(offset);
        int column = offset - linestart[line] + 1;
        return new Location(file, line, column);
    }

    internal int Size => size;

    public override int GetHashCode()
    {
        var hc = new HashCode();
        foreach (int x in linestart)
        {
            hc.Add(x);
        }
        hc.Add(file);
        hc.Add(size);
        return hc.ToHashCode();
    }

    public override bool Equals(object? other)
    {
        if (other is not FileLocations that)
        {
            return false;
        }
        return this.size == that.size
            && this.linestart.AsSpan().SequenceEqual(that.linestart)
            && this.file == that.file;
    }

    private static int[] ComputeLinestart(char[] buffer)
    {
        // Compute the size.
        int size = 2;
        for (int i = 0; i < buffer.Length; i++)
        {
            if (buffer[i] == '\n')
            {
                size++;
            }
        }
        int[] linestart = new int[size];

        int index = 0;
        linestart[index++] = 0; // The 0th line does not exist.
        linestart[index++] = 0; // The first line ("line 1") starts at offset 0.

        for (int i = 0; i < buffer.Length; i++)
        {
            if (buffer[i] == '\n')
            {
                linestart[index++] = i + 1;
            }
        }
        return linestart;
    }
}
