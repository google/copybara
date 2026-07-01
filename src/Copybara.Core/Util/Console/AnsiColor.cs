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

namespace Copybara.Util.Console;

/// <summary>
/// Colors to print messages in the console.
/// </summary>
public enum AnsiColor
{
    Reset,
    Black,
    Red,
    Green,
    Yellow,
    Blue,
    Purple,
    Cyan,
    White,
}

/// <summary>
/// Helpers associated with <see cref="AnsiColor"/> that map each color to its ANSI escape code and
/// wrap text with the color, resetting afterwards.
/// </summary>
public static class AnsiColors
{
    private static string Code(AnsiColor color) => color switch
    {
        AnsiColor.Reset => "[0m",
        AnsiColor.Black => "[30m",
        AnsiColor.Red => "[31m",
        AnsiColor.Green => "[32m",
        AnsiColor.Yellow => "[33m",
        AnsiColor.Blue => "[34m",
        AnsiColor.Purple => "[35m",
        AnsiColor.Cyan => "[36m",
        AnsiColor.White => "[37m",
        _ => "",
    };

    /// <summary>Wraps <paramref name="text"/> with the color's ANSI code, resetting afterwards.</summary>
    public static string Write(this AnsiColor color, string text)
    {
        return Code(color) + text + Code(AnsiColor.Reset);
    }
}
