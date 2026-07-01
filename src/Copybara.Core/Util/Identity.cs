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

using System.Security.Cryptography;
using System.Text;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Logging.Abstractions;

namespace Copybara.Util;

/// <summary>
/// Utility methods for Identity, which computes an identity based on workflowName, contextReference,
/// configPath, and workflowIdentityUser and allows us to reuse the destination changes. Port of
/// <c>com.google.copybara.util.Identity</c>.
/// </summary>
public static class Identity
{
    private static readonly ILogger Logger = NullLogger.Instance;

    public static string ComputeIdentity(
        string type,
        string @ref,
        string workflowName,
        string configPath,
        string? workflowIdentityUser)
    {
        // Mirrors Guava's MoreObjects.toStringHelper(type) output format:
        //   <type>{type=workflow, config_path=<configPath>, workflow_name=<name>, context_ref=<ref>}
        var helper = new ToStringHelper(type)
            .Add("type", "workflow")
            .Add("config_path", configPath)
            .Add("workflow_name", workflowName)
            .Add("context_ref", @ref);
        return HashIdentity(helper, workflowIdentityUser);
    }

    public static string HashIdentity(ToStringHelper helper, string? workflowIdentityUser)
    {
        helper.Add(
            "user",
            workflowIdentityUser ?? Environment.UserName);
        string identity = helper.ToString();
        byte[] digest = MD5.HashData(Encoding.UTF8.GetBytes(identity));
        // Guava BaseEncoding.base16() produces uppercase hex.
        string hash = Convert.ToHexString(digest);
        // Important to log the source of the hash and the hash for debugging purposes.
        Logger.LogInformation(
            "Computed migration identity hash for {Identity} as {Hash} ", identity, hash);
        return hash;
    }
}

/// <summary>
/// A minimal port of Guava's <c>MoreObjects.ToStringHelper</c> producing the same textual layout
/// used to derive a stable identity hash.
/// </summary>
public sealed class ToStringHelper
{
    private readonly string _className;
    private readonly List<(string Name, string Value)> _entries = new();

    public ToStringHelper(string className) => _className = className;

    public ToStringHelper Add(string name, string? value)
    {
        _entries.Add((name, value ?? "null"));
        return this;
    }

    public override string ToString()
    {
        var sb = new StringBuilder(_className).Append('{');
        for (int i = 0; i < _entries.Count; i++)
        {
            if (i > 0)
            {
                sb.Append(", ");
            }
            sb.Append(_entries[i].Name).Append('=').Append(_entries[i].Value);
        }
        return sb.Append('}').ToString();
    }
}
