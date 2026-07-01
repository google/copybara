/*
 * Copyright (C) 2023 Google Inc.
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

using System.Text;

namespace Copybara.Python;

/// <summary>Utility class for extracting metadata from a python metadata file.</summary>
public static class PackageMetadata
{
    /// <summary>
    /// Parse the metadata file and return a list of key value metadata pairs.
    /// </summary>
    /// <exception cref="EmptyMetadataException">
    /// if no metadata are found. Some metadata fields are required, so there should always be
    /// something.
    /// </exception>
    /// <exception cref="IOException"/>
    internal static IReadOnlyList<KeyValuePair<string, string>> GetMetadata(CheckoutPath metadataPath)
    {
        List<KeyValuePair<string, string>> metadata = ExtractMetadata(metadataPath.FullPath());
        if (metadata.Count == 0)
        {
            throw new EmptyMetadataException(
                $"no metadata fields found for file {metadataPath}");
        }

        return metadata;
    }

    /// <exception cref="IOException"/>
    private static List<KeyValuePair<string, string>> ExtractMetadata(string metadataPath)
    {
        var metadata = new List<KeyValuePair<string, string>>();

        // The python package metadata format is based off email headers:
        // https://packaging.python.org/en/latest/specifications/core-metadata/
        // Headers are RFC 822-style: "Key: value" lines, with continuation ("folded") lines that
        // begin with whitespace, and are terminated by the first blank line (after which comes the
        // free-form long description body, which we ignore).
        try
        {
            using var reader = new StreamReader(File.OpenRead(metadataPath));
            string? currentName = null;
            var currentValue = new StringBuilder();
            string? line;

            while ((line = reader.ReadLine()) != null)
            {
                // A blank line marks the end of the headers.
                if (line.Length == 0)
                {
                    break;
                }

                if (char.IsWhiteSpace(line[0]))
                {
                    // Continuation of the previous header value.
                    if (currentName != null)
                    {
                        currentValue.Append(line.TrimStart());
                    }

                    continue;
                }

                // Flush any header we were accumulating.
                if (currentName != null)
                {
                    metadata.Add(new KeyValuePair<string, string>(currentName, currentValue.ToString()));
                    currentValue.Clear();
                }

                int colon = line.IndexOf(':');
                if (colon < 0)
                {
                    // Not a valid header line; skip it.
                    currentName = null;
                    continue;
                }

                currentName = line.Substring(0, colon);
                // Skip the ": " separator; trim a single leading space per RFC 822.
                string value = line.Substring(colon + 1);
                currentValue.Append(value.Length > 0 && value[0] == ' ' ? value.Substring(1) : value);
            }

            if (currentName != null)
            {
                metadata.Add(new KeyValuePair<string, string>(currentName, currentValue.ToString()));
            }
        }
        catch (Exception e) when (e is not IOException)
        {
            throw new IOException("failed to read metadata headers", e);
        }

        return metadata;
    }

    /// <summary>No metadata was found in the file.</summary>
    public class EmptyMetadataException : Exception
    {
        internal EmptyMetadataException(string message)
            : base(message)
        {
        }
    }
}
