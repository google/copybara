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

using System.Collections.Immutable;
using System.Text.RegularExpressions;
using Copybara.Exceptions;

namespace Copybara.Git.GerritApi;

/// <summary>Exception that maps to Gerrit Http error codes.</summary>
public class GerritApiException : RepoException
{
    public static readonly Regex ErrorPattern = new(
        ".*<pre>(.*)</pre>.*",
        RegexOptions.Singleline | RegexOptions.IgnoreCase | RegexOptions.Multiline);

    private static readonly ImmutableDictionary<int, ResponseCodeValue> CodeMap =
        Enum.GetValues<ResponseCodeValue>()
            .ToImmutableDictionary(rc => (int)rc);

    private readonly string _baseMessage;
    private readonly ResponseCodeValue _responseCode;
    private readonly string _gerritResponseMsg;
    private readonly string _gerritRequestMsg;
    private readonly int _exitCode;

    public GerritApiException(
        int exitCode, string message, string gerritResponseMsg, string gerritRequest)
        : base(message)
    {
        _baseMessage = message;
        _exitCode = exitCode;
        _responseCode = ParseResponseCode(exitCode);
        _gerritResponseMsg = gerritResponseMsg;
        _gerritRequestMsg = gerritRequest;
    }

    public ResponseCodeValue GetResponseCode() => _responseCode;

    public int GetExitCode() => _exitCode;

    private static ResponseCodeValue ParseResponseCode(int code) =>
        CodeMap.TryGetValue(code, out var rc) ? rc : ResponseCodeValue.UNKNOWN;

    public override string Message =>
        string.Format(
            "{0}: Received error with code {1} from Gerrit: {2}\n\nThe request was:\n\n{3}\n\n"
            + "The full response was:\n\n{4}",
            _baseMessage,
            _exitCode,
            ExtractError(),
            _gerritRequestMsg,
            _gerritResponseMsg);

    private string ExtractError()
    {
        var matcher = ErrorPattern.Match(_gerritResponseMsg);
        if (matcher.Success)
        {
            return matcher.Groups[1].Value;
        }

        return _gerritResponseMsg;
    }

    public string GetGerritResponseMsg() => _gerritResponseMsg;

    /// <summary>
    /// Gerrit known response codes.
    ///
    /// <para>Note that UNKNOWN will be used for any other not in this list.</para>
    /// </summary>
    /// <remarks>NOTE(port): named <c>ResponseCodeValue</c> to avoid colliding with the
    /// <c>GetResponseCode()</c> accessor within the same type; the Java enum is
    /// <c>GerritApiException.ResponseCode</c>.</remarks>
    public enum ResponseCodeValue
    {
        UNKNOWN = 0,
        BAD_REQUEST = 400,
        FORBIDDEN = 403,
        NOT_FOUND = 404,
        METHOD_NOT_ALLOWED = 405,
        CONFLICT = 409,
        PRECONDITION_FAILED = 412,
        UNPROCESSABLE_ENTITY = 422,
    }
}
