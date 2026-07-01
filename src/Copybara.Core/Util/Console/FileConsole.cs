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

using System.Globalization;
using System.Text;
using Copybara.Common;
using MessageType = Copybara.Util.Console.Message.MessageType;

namespace Copybara.Util.Console;

/// <summary>
/// A <see cref="Console"/> capable of writing the output into a file and to a delegate console.
///
/// <para>If any of the file operations fail, the console won't try to write to the file anymore and
/// will only call to the delegate console.</para>
///
/// <para>Caller is responsible for closing this console to free resources.</para>
///
/// <para>The console can be configured to flush on a fixed rate intervals.</para>
/// </summary>
public class FileConsole : DelegateConsole
{
    private const string DatePrefixFmt = "MMdd HH:mm:ss.fff";

    protected readonly string FilePath;
    private readonly Timer? _flushingTimer;

    // Serializes writes to the underlying writer, standing in for the Java single-thread executor.
    private readonly object _loggingLock = new();
    private readonly object _writerLock = new();

    private bool _shutdown = false;
    private bool _failed = false;
    private TextWriter? _writer;

    /// <summary>Creates a new <see cref="FileConsole"/>.</summary>
    /// <param name="delegate">A delegate console.</param>
    /// <param name="filePath">
    /// A file path to write to. The parent directories must be created in advance.
    /// </param>
    /// <param name="consoleFlushRate">How often to flush this file console.</param>
    public FileConsole(Console @delegate, string filePath, TimeSpan consoleFlushRate)
        : base(@delegate)
    {
        FilePath = Preconditions.CheckNotNull(filePath);
        if (consoleFlushRate <= TimeSpan.Zero)
        {
            _flushingTimer = null;
        }
        else
        {
            _flushingTimer = new Timer(_ => Flush(), null, consoleFlushRate, consoleFlushRate);
        }
    }

    protected override void HandleMessage(MessageType type, string message)
    {
        TextWriter? writer = GetWriter();
        if (writer == null)
        {
            return;
        }
        // Submitting to a shut-down executor throws, so this has to be synchronized to avoid shutting
        // down in-between from another thread.
        lock (_loggingLock)
        {
            if (_shutdown)
            {
                return;
            }
            string now = DateTime.Now.ToString(DatePrefixFmt, CultureInfo.InvariantCulture);
            DoWrite(writer, $"{now} {type}: {message}\n");
        }
    }

    private void DoWrite(TextWriter writer, string s)
    {
        if (_failed)
        {
            return;
        }
        try
        {
            writer.Write(s);
        }
        catch (IOException)
        {
            _failed = true;
        }
    }

    private void Flush()
    {
        if (_failed)
        {
            return;
        }
        TextWriter? writer = GetWriter();
        if (writer != null)
        {
            try
            {
                lock (_loggingLock)
                {
                    writer.Flush();
                }
            }
            catch (IOException)
            {
                _failed = true;
            }
        }
    }

    private TextWriter? GetWriter()
    {
        lock (_writerLock)
        {
            if (_writer == null)
            {
                _writer = InitWriter();
            }
            return _writer;
        }
    }

    private TextWriter? InitWriter()
    {
        try
        {
            // TRUNCATE_EXISTING if the file exists, otherwise CREATE_NEW: FileMode.Create matches
            // both cases (create or truncate).
            var stream = new FileStream(FilePath, FileMode.Create, FileAccess.Write, FileShare.Read);
            return new StreamWriter(stream, new UTF8Encoding(false));
        }
        catch (IOException)
        {
            _failed = true;
        }
        return null;
    }

    public override void Dispose()
    {
        base.Dispose();
        lock (_loggingLock)
        {
            _shutdown = true;
        }
        _flushingTimer?.Dispose();
        try
        {
            if (_writer == null)
            {
                return;
            }
            _writer.Dispose();
        }
        catch (IOException)
        {
            _failed = true;
        }
    }
}
