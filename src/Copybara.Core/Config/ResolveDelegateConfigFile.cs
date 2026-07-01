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
using Copybara.Exceptions;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Logging.Abstractions;

namespace Copybara.Config;

/// <summary>
/// A <see cref="ConfigFile"/> that delegates to a main config file and falls back to a secondary one
/// for file resolution, if necessary.
///
/// <para>This is useful for cases where generated in-memory configurations have dependencies on
/// persisted configurations.</para>
/// </summary>
public sealed class ResolveDelegateConfigFile : ConfigFile
{
    private static readonly ILogger Logger = NullLogger.Instance;

    private readonly ConfigFile _mainConfigFile;
    private readonly ConfigFile _secondConfigFile;

    public ResolveDelegateConfigFile(ConfigFile mainConfigFile, ConfigFile secondConfigFile)
    {
        _mainConfigFile = Preconditions.CheckNotNull(mainConfigFile);
        _secondConfigFile = Preconditions.CheckNotNull(secondConfigFile);
    }

    public ConfigFile Resolve(string path)
    {
        try
        {
            return _mainConfigFile.Resolve(path);
        }
        catch (CannotResolveLabel)
        {
            Logger.LogInformation(
                "Could not resolve {Path} from {Main}. Resolving from {Second}.",
                path, _mainConfigFile.Path(), _secondConfigFile.Path());
            try
            {
                return _secondConfigFile.Resolve(path);
            }
            catch (CannotResolveLabel crl)
            {
                throw new CannotResolveLabel(
                    string.Format(
                        "Could not resolve main config or second config to path '{0}'. Main config"
                        + " path is '{1}', second config path is '{2}'",
                        path, _mainConfigFile.Path(), _secondConfigFile.Path()),
                    crl);
            }
        }
    }

    public string Path() => _mainConfigFile.Path();

    public byte[] ReadContentBytes() => _mainConfigFile.ReadContentBytes();

    public string GetIdentifier() => _mainConfigFile.GetIdentifier();
}
