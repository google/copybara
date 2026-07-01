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

using System.Security.Cryptography;
using System.Text;
using Copybara.Config;
using Copybara.Exceptions;
using Starlark.Annot;
using Starlark.Eval;

namespace Copybara.Hashing;

/// <summary>Hashing utilities.</summary>
[StarlarkBuiltin("hashing", Doc = "utilities for hashing")]
public class HashingModule : IStarlarkValue
{
    [StarlarkMethod(
        "path_md5_sum",
        Doc =
            "Return the md5 hash of a file at a checkout path. Do not use unless working with legacy"
            + " systems that require MD5.\n"
            + "WARNING: do not use unless working with legacy systems that require MD5")]
    public string PathMd5Sum(
        [Param(Name = "path", Doc = "checkout path pointing to a file to be hashed")]
        CheckoutPath path)
    {
        using var hashFunc = MD5.Create();
        return HashFile(path.FullPath(), hashFunc);
    }

    [StarlarkMethod(
        "path_sha256_sum",
        Doc = "Return the sha256 hash of a file at a checkout path")]
    public string PathSha256Sum(
        [Param(Name = "path", Doc = "checkout path pointing to a file to be hashed")]
        CheckoutPath path)
    {
        using var hashFunc = SHA256.Create();
        return HashFile(path.FullPath(), hashFunc);
    }

    [StarlarkMethod(
        "str_sha256_sum",
        Doc = "Return the hash of a list of objects based on the algorithm specified")]
    public string HashStringWithSha256(
        [Param(
            Name = "input",
            Named = true,
            Doc = "One or more string inputs to hash.",
            AllowedTypes = new[] { typeof(ISequence<object?>), typeof(string) })]
        object input)
    {
        if (input is string singleInput)
        {
            return ToHex(SHA256.HashData(Encoding.UTF8.GetBytes(singleInput)));
        }

        var stringInputs = SkylarkUtil.ConvertStringList(input, "input");
        if (stringInputs.Count == 0)
        {
            throw new ValidationException(
                "hashing.hash_str_with_sha256 cannot be called with an empty object list.");
        }

        using var hasher = IncrementalHash.CreateHash(HashAlgorithmName.SHA256);
        foreach (string stringInput in stringInputs)
        {
            hasher.AppendData(Encoding.UTF8.GetBytes(stringInput));
        }
        return ToHex(hasher.GetHashAndReset());
    }

    private static string HashFile(string hashPath, HashAlgorithm hashFunc)
    {
        using var stream = File.OpenRead(hashPath);
        return ToHex(hashFunc.ComputeHash(stream));
    }

    private static string ToHex(byte[] bytes) => Convert.ToHexStringLower(bytes);
}
