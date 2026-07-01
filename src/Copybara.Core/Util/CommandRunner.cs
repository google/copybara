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

using System.Diagnostics;
using System.Text;
using Copybara.Common;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Logging.Abstractions;

namespace Copybara.Util;

/// <summary>
/// Allows running a <see cref="Command"/> with easier stderr/stdout and logging management. Port of
/// <c>com.google.copybara.util.CommandRunner</c>. Execution is performed with
/// <c>System.Diagnostics.Process</c> rather than the Bazel shell library; stdout/stderr are captured,
/// a timeout kills the process, and the exit status is reported through
/// <see cref="CommandOutputWithStatus"/> and the command exception hierarchy.
/// </summary>
public sealed class CommandRunner
{
    private static readonly ILogger Logger = NullLogger.Instance;

    /// <summary>No input for the command.</summary>
    public static readonly byte[] NoInput = Array.Empty<byte>();

    /// <summary>By default, we kill the command after 15 minutes.</summary>
    public static readonly TimeSpan DefaultTimeout = TimeSpan.FromMinutes(15);

    public const int MaxCommandLength = 40000;

    public static readonly byte[] AfterLimitSuffix =
        Encoding.UTF8.GetBytes("... (Rest of the output skipped)\n");

    private readonly Command _cmd;
    private readonly bool _verbose;
    private readonly byte[] _input;
    private readonly int _maxOutLogLines;
    private readonly TimeSpan _timeout;
    private readonly Stream? _asyncStdoutStream;
    private readonly Stream? _asyncErrStream;

    private CommandRunner(
        Command cmd,
        bool verbose,
        byte[] input,
        int maxOutLogLines,
        TimeSpan timeout,
        Stream? asyncStdoutStream,
        Stream? asyncErrStream)
    {
        _cmd = Preconditions.CheckNotNull(cmd);
        _verbose = verbose;
        _input = Preconditions.CheckNotNull(input);
        _maxOutLogLines = maxOutLogLines;
        _timeout = timeout;
        _asyncStdoutStream = asyncStdoutStream;
        _asyncErrStream = asyncErrStream;
    }

    public CommandRunner(Command cmd)
        : this(cmd, false, NoInput, -1, DefaultTimeout, null, null)
    {
    }

    public CommandRunner(Command cmd, TimeSpan timeout)
        : this(cmd, false, NoInput, -1, timeout, null, null)
    {
    }

    /// <summary>Sets the verbose level for the command execution.</summary>
    public CommandRunner WithVerbose(bool verbose) =>
        new(_cmd, verbose, _input, _maxOutLogLines, _timeout, _asyncStdoutStream, _asyncErrStream);

    /// <summary>Sets the input for the command execution.</summary>
    public CommandRunner WithInput(byte[] input) =>
        new(_cmd, _verbose, input, _maxOutLogLines, _timeout, _asyncStdoutStream, _asyncErrStream);

    /// <summary>Sets the maximum number of output lines logged per stream.</summary>
    public CommandRunner WithMaxStdOutLogLines(int lines) =>
        new(_cmd, _verbose, _input, lines, _timeout, _asyncStdoutStream, _asyncErrStream);

    /// <summary>Sets a stream to redirect stdOut output to.</summary>
    public CommandRunner WithStdOutStream(Stream stream) =>
        new(_cmd, _verbose, _input, _maxOutLogLines, _timeout, stream, _asyncErrStream);

    /// <summary>Sets a stream to redirect stdErr output to.</summary>
    public CommandRunner WithStdErrStream(Stream stream) =>
        new(_cmd, _verbose, _input, _maxOutLogLines, _timeout, _asyncStdoutStream, stream);

    /// <summary>
    /// Executes the <see cref="Command"/> with the given input and writes to the console and the log
    /// depending on the exit code of the command and the verbose flag.
    /// </summary>
    public CommandOutputWithStatus Execute()
    {
        var stopwatch = Stopwatch.StartNew();
        var argv = _cmd.GetCommandLineElements();
        string startMsg = ShellUtils.PrettyPrintArgv(argv);
        startMsg = startMsg.Length > MaxCommandLength
            ? startMsg.Substring(0, MaxCommandLength) + "..."
            : startMsg;
        string validStartMsg = "Executing [" + startMsg + "]";
        Logger.LogInformation("{Msg}", validStartMsg);
        if (_verbose)
        {
            System.Console.Error.WriteLine(validStartMsg);
        }

        var stdoutCollector = new MemoryStream();
        var stderrCollector = new MemoryStream();
        if (_asyncStdoutStream != null)
        {
            byte[] note = Encoding.UTF8.GetBytes("stdOut redirected to external observer.");
            stdoutCollector.Write(note, 0, note.Length);
        }
        if (_asyncErrStream != null)
        {
            byte[] note = Encoding.UTF8.GetBytes("stdErr redirected to external observer.");
            stderrCollector.Write(note, 0, note.Length);
        }

        Stream stdoutStream = CommandOutputStream(_asyncStdoutStream ?? stdoutCollector, _maxOutLogLines);
        Stream stderrStream = CommandOutputStream(_asyncErrStream ?? stderrCollector, _maxOutLogLines);

        string commandName = argv[0];
        TerminationStatus? exitStatus = null;
        bool timedOut = false;
        try
        {
            var startInfo = new ProcessStartInfo
            {
                FileName = commandName,
                RedirectStandardInput = true,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                UseShellExecute = false,
                CreateNoWindow = true,
            };
            for (int i = 1; i < argv.Count; i++)
            {
                startInfo.ArgumentList.Add(argv[i]);
            }
            var env = _cmd.GetEnvironmentVariables();
            if (env != null)
            {
                startInfo.Environment.Clear();
                foreach (var kvp in env)
                {
                    startInfo.Environment[kvp.Key] = kvp.Value;
                }
            }
            var workingDir = _cmd.GetWorkingDirectory();
            if (workingDir != null)
            {
                startInfo.WorkingDirectory = workingDir;
            }

            using var process = new Process { StartInfo = startInfo };

            var stdoutDone = new ManualResetEventSlim(false);
            var stderrDone = new ManualResetEventSlim(false);

            try
            {
                process.Start();
            }
            catch (Exception e)
            {
                throw new CommandException(_cmd, e);
            }

            var pumpOut = PumpAsync(process.StandardOutput.BaseStream, stdoutStream, stdoutDone);
            var pumpErr = PumpAsync(process.StandardError.BaseStream, stderrStream, stderrDone);

            // Write input then close stdin.
            try
            {
                if (_input.Length > 0)
                {
                    process.StandardInput.BaseStream.Write(_input, 0, _input.Length);
                }
                process.StandardInput.BaseStream.Flush();
                process.StandardInput.Close();
            }
            catch (IOException)
            {
                // The process may have already exited and closed its stdin; ignore.
            }

            bool exited = process.WaitForExit((int)Math.Min(_timeout.TotalMilliseconds, int.MaxValue));
            if (!exited)
            {
                timedOut = true;
                try
                {
                    process.Kill(entireProcessTree: true);
                }
                catch (Exception)
                {
                    // best effort
                }
                process.WaitForExit();
            }

            // Ensure the output pumps drain fully after exit.
            stdoutDone.Wait();
            stderrDone.Wait();
            pumpOut.GetAwaiter().GetResult();
            pumpErr.GetAwaiter().GetResult();

            int rawResult = timedOut ? (128 + 15 /* SIGTERM */) : process.ExitCode;
            var status = new TerminationStatus(rawResult);
            exitStatus = status;

            byte[] stdoutBytes = stdoutCollector.ToArray();
            byte[] stderrBytes = stderrCollector.ToArray();

            if (timedOut)
            {
                var result = new CommandResult(stdoutBytes, stderrBytes, status);
                MaybeTreatTimeout(stdoutBytes, stderrBytes, true,
                    new AbnormalTerminationException(_cmd, result, status.ToString()));
            }

            if (!status.Success())
            {
                var result = new CommandResult(stdoutBytes, stderrBytes, status);
                string message =
                    $"Process '{commandName}' exited with status {status.ToShortString()}";
                throw new BadExitStatusWithOutputException(_cmd, result, message, stdoutBytes, stderrBytes);
            }

            return new CommandOutputWithStatus(status, stdoutBytes, stderrBytes);
        }
        finally
        {
            if (_maxOutLogLines != 0)
            {
                LogOutput(
                    LogLevel.Information, $"'{commandName}' STDOUT: ", stdoutCollector, _maxOutLogLines);
                LogOutput(
                    LogLevel.Information, $"'{commandName}' STDERR: ", stderrCollector, _maxOutLogLines);
            }

            string finishMsg;
            if (timedOut)
            {
                finishMsg = string.Format(
                    "Command '{0}' was killed after timeout. Execution time {1}. {2}",
                    commandName,
                    FormatDuration(stopwatch.Elapsed),
                    exitStatus != null ? exitStatus.ToString() : "(No exit status)");
                Logger.LogError("{Msg}", finishMsg);
            }
            else
            {
                finishMsg = string.Format(
                    "Command '{0}' finished in {1}. {2}",
                    commandName,
                    FormatDuration(stopwatch.Elapsed),
                    exitStatus != null ? exitStatus.ToString() : "(No exit status)");
                Logger.LogInformation("{Msg}", finishMsg);
            }
            if (_verbose)
            {
                System.Console.Error.WriteLine(finishMsg);
            }
        }
    }

    private static Task PumpAsync(Stream source, Stream destination, ManualResetEventSlim done)
    {
        return Task.Run(() =>
        {
            try
            {
                var buffer = new byte[8192];
                int read;
                while ((read = source.Read(buffer, 0, buffer.Length)) > 0)
                {
                    destination.Write(buffer, 0, read);
                }
                destination.Flush();
            }
            catch (Exception)
            {
                // Stream closed on process exit; ignore.
            }
            finally
            {
                done.Set();
            }
        });
    }

    /// <summary>
    /// Format a duration to a human-readable string. Assumes the duration is less than 24 hours,
    /// which should always be true for a command.
    /// </summary>
    private static string FormatDuration(TimeSpan duration) => duration.ToString(@"mm\:ss\.fff");

    private void MaybeTreatTimeout(
        byte[] stdout, byte[] stderr, bool hasTimedOut, AbnormalTerminationException e)
    {
        if (!hasTimedOut)
        {
            return;
        }
        string msg = string.Format(
            "Command '{0}' killed by Copybara after timeout ({1}s)."
                + " If this fails during a fetch or push use --repo-timeout flag."
                + " If this fails during checkout or another point, use --commands-timeout flag.\n"
                + "Exit info: {2}",
            _cmd.GetCommandLineElements()[0],
            (long)_timeout.TotalSeconds,
            e.GetResult().TerminationStatus);
        throw new CommandTimeoutException(
            e.GetCommand(), e.GetResult(), msg, stdout, stderr, _timeout);
    }

    /// <summary>Creates the OutputStream to feed the process output to.</summary>
    private Stream CommandOutputStream(Stream outputStream, int maxOutLogLines)
    {
        // If verbose we stream to the user console too.
        if (!_verbose)
        {
            return outputStream;
        }
        var console = System.Console.OpenStandardError();
        var limited = new LimitFilterOutputStream(
            console,
            // Assume a line has ~200 characters. If no limit, ~10k lines.
            maxOutLogLines > 0 ? maxOutLogLines * 200 : 10000 * 200,
            AfterLimitSuffix);
        return new MultiplexOutputStream(limited, outputStream);
    }

    private static void LogOutput(
        LogLevel level, string prefix, MemoryStream outputBytes, int maxLogLines)
    {
        string s = Encoding.UTF8.GetString(outputBytes.ToArray()).Trim();
        if (s.Length == 0)
        {
            return;
        }
        int lines = 0;
        foreach (var line in s.Split(Environment.NewLine))
        {
            Logger.Log(level, "{Prefix}{Line}", prefix, line);
            lines++;
            if (maxLogLines >= 0 && lines >= maxLogLines)
            {
                Logger.Log(level, "{Prefix}... truncated after {Max} line(s)", prefix, maxLogLines);
                break;
            }
        }
    }

    /// <summary>An <see cref="Stream"/> that writes to two underlying streams.</summary>
    private sealed class MultiplexOutputStream : Stream
    {
        private readonly Stream _s1;
        private readonly Stream _s2;

        public MultiplexOutputStream(Stream s1, Stream s2)
        {
            _s1 = s1;
            _s2 = s2;
        }

        public override bool CanRead => false;
        public override bool CanSeek => false;
        public override bool CanWrite => true;
        public override long Length => throw new NotSupportedException();

        public override long Position
        {
            get => throw new NotSupportedException();
            set => throw new NotSupportedException();
        }

        public override void Write(byte[] buffer, int offset, int count)
        {
            _s1.Write(buffer, offset, count);
            _s2.Write(buffer, offset, count);
        }

        public override void WriteByte(byte value)
        {
            _s1.WriteByte(value);
            _s2.WriteByte(value);
        }

        public override void Flush()
        {
            _s1.Flush();
            _s2.Flush();
        }

        public override int Read(byte[] buffer, int offset, int count) =>
            throw new NotSupportedException();

        public override long Seek(long offset, SeekOrigin origin) =>
            throw new NotSupportedException();

        public override void SetLength(long value) => throw new NotSupportedException();
    }
}
