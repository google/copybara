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

using Copybara.Common;
using Copybara.Exceptions;
using Starlark.Annot;
using Starlark.Eval;
using StarlarkRt = Starlark.Eval.Starlark;

namespace Copybara.Buildozer;

/// <summary>Represents a possibly-reversible Buildozer command.</summary>
[StarlarkBuiltin("Command", Doc = "Buildozer command type")]
public sealed class Command : IStarlarkPrintableValue
{
    private readonly string _command;
    private readonly string? _reverse;

    private Command(string command, string? reverse)
    {
        _command = Preconditions.CheckNotNull(command);
        Preconditions.CheckArgument(command.Trim().Length != 0, "Found empty command");
        Preconditions.CheckArgument(
            reverse == null || reverse.Trim().Length != 0,
            "Found empty reversal command. Command was: {0}",
            command);

        _reverse = reverse;
        new ArgValidator(command).Validate();
        if (reverse != null)
        {
            new ArgValidator(reverse).Validate();
        }
    }

    internal static Command FromConfig(string command, string? reverse)
    {
        if (reverse == null)
        {
            List<string> components = command
                .Split(' ', 2, StringSplitOptions.RemoveEmptyEntries)
                .ToList();
            if (components.Count == 2)
            {
                reverse = ReverseArgs(components[0], components[1]);
            }
        }

        try
        {
            return new Command(command, reverse);
        }
        catch (ArgumentException ex)
        {
            throw new EvalException(ex.Message, ex);
        }
    }

    private sealed class ArgValidator
    {
        private readonly List<string> _argv;

        internal ArgValidator(string command)
        {
            _argv = SplitArgv(command);
        }

        private void ValidateCount(bool valid, string requirement)
        {
            Preconditions.CheckArgument(
                valid,
                "'{0}' requires {1}, but got: {2}",
                _argv[0],
                requirement,
                ArgCount());
        }

        private int ArgCount() => _argv.Count - 1;

        internal void Validate()
        {
            Preconditions.CheckArgument(
                _argv.Count != 0, "Expected an operation, but got empty string.");
            switch (_argv[0])
            {
                case "del_subinclude":
                case "rename":
                case "copy":
                case "copy_no_overwrite":
                    ValidateCount(ArgCount() == 2, "exactly 2 arguments");
                    break;
                case "fix":
                case "print":
                case "remove_comment":
                    break; // can take 0+
                case "replace_subinclude":
                case "move":
                    ValidateCount(ArgCount() >= 3, "at least 3 arguments");
                    break;
                case "delete":
                    ValidateCount(ArgCount() == 0, "exactly 0 arguments");
                    break;
                case "replace":
                    ValidateCount(ArgCount() == 3, "exactly 3 arguments");
                    break;
                case "comment":
                case "remove":
                case "set":
                case "set_if_absent":
                    ValidateCount(ArgCount() >= 1, "at least 1 argument");
                    break;
                case "add":
                case "new_load":
                case "new":
                    ValidateCount(ArgCount() >= 2, "at least 2 arguments");
                    break;
                default:
                    // We assume that all unary operations are covered.
                    Preconditions.CheckArgument(
                        ArgCount() > 1, "Expected an operation, but got '{0}'.", _argv[0]);
                    break;
            }
        }
    }

    public bool IsImmutable() => true;

    public void Repr(Printer printer, StarlarkSemantics semantics)
    {
        printer.Append(
            string.Format("buildozer.cmd({0}, reverse = {1})", _command, _reverse));
    }

    /// <summary>
    /// Returns the command and arguments concatenated, which can be passed directly to Buildozer.
    /// </summary>
    public override string ToString() => _command;

    /// <summary>Returns the reverse version of this command.</summary>
    /// <exception cref="NonReversibleValidationException">if this instance is not reversible</exception>
    internal Command Reverse()
    {
        if (_reverse == null)
        {
            throw new NonReversibleValidationException(
                "The current command is not auto-reversible and a reverse was not provided: "
                + _command);
        }

        return new Command(_reverse, _command);
    }

    /// <summary>Calculates the reversal of a command whose reversal has not been manually specified.</summary>
    private static string? ReverseArgs(string commandName, string args)
    {
        switch (commandName)
        {
            case "add":
                return "remove " + args;
            case "remove":
                if (args.Contains(' '))
                {
                    // Do not reverse 'remove attr' operation. Only 'remove attr value'
                    return "add " + args;
                }
                return null;
            case "replace":
                List<string> reverseArgs = SplitArgv(args);
                if (reverseArgs.Count != 3)
                {
                    throw StarlarkRt.Errorf(
                        "Cannot reverse '{0} {1}', expected three arguments, but found {2}.",
                        commandName, args, reverseArgs.Count);
                }
                (reverseArgs[1], reverseArgs[2]) = (reverseArgs[2], reverseArgs[1]);
                return "replace " + string.Join(' ', reverseArgs);
        }
        return null;
    }

    private static List<string> SplitArgv(string argv) =>
        argv.Split(' ', StringSplitOptions.RemoveEmptyEntries).ToList();
}
