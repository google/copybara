// Copyright 2024 The Bazel Authors. All rights reserved.
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

using Starlark.Eval;
using Starlark.Syntax;
using Xunit;
using SL = Starlark.Eval.Starlark;
using FileOptions = Starlark.Syntax.FileOptions;

namespace Copybara.Tests;

public class StarlarkEvalTests
{
    private static Module Exec(string program, out StarlarkThread thread)
    {
        Mutability mu = Mutability.Create("test");
        thread = StarlarkThread.Create(mu, StarlarkSemantics.DEFAULT);
        Module module = Module.WithPredeclared(StarlarkSemantics.DEFAULT, new Dictionary<string, object>());
        ParserInput input = ParserInput.FromString(program, "test.star");
        SL.ExecFile(input, FileOptions.DEFAULT, module, thread);
        return module;
    }

    private static object? EvalExpr(string expr)
    {
        Mutability mu = Mutability.Create("test");
        StarlarkThread thread = StarlarkThread.Create(mu, StarlarkSemantics.DEFAULT);
        Module module = Module.WithPredeclared(StarlarkSemantics.DEFAULT, new Dictionary<string, object>());
        ParserInput input = ParserInput.FromString(expr, "expr.star");
        return SL.Eval(input, FileOptions.DEFAULT, module, thread);
    }

    [Fact]
    public void ListAndLen()
    {
        Module m = Exec("x = [1, 2, 3]\ny = len(x)\n", out _);
        object? y = m.GetGlobal("y");
        Assert.Equal(StarlarkInt.Of(3), y);
    }

    [Fact]
    public void DefAndCall()
    {
        Module m = Exec("def add(a, b):\n    return a + b\nz = add(2, 40)\n", out _);
        Assert.Equal(StarlarkInt.Of(42), m.GetGlobal("z"));
    }

    [Fact]
    public void DictAndStringMethods()
    {
        Module m = Exec(
            "d = {\"a\": 1, \"b\": 2}\n" +
            "keys = sorted(d.keys())\n" +
            "joined = \",\".join(keys)\n" +
            "up = joined.upper()\n",
            out _);
        Assert.Equal("A,B", m.GetGlobal("up"));
    }

    [Fact]
    public void ComprehensionAndBuiltins()
    {
        Module m = Exec("sq = [i * i for i in range(4)]\ntotal = sq\n", out _);
        var list = Assert.IsAssignableFrom<StarlarkList>(m.GetGlobal("sq"));
        Assert.Equal(4, list.Count);
        Assert.Equal(StarlarkInt.Of(9), list[3]);
    }

    [Fact]
    public void EvalExpressionArithmetic()
    {
        Assert.Equal(StarlarkInt.Of(7), EvalExpr("3 + 4"));
        Assert.Equal("hello world", EvalExpr("\"hello \" + \"world\""));
        Assert.Equal(true, EvalExpr("1 < 2 and 2 < 3"));
    }

    [Fact]
    public void StringFormat()
    {
        Assert.Equal("x=5", EvalExpr("\"x={}\".format(5)"));
    }

    [Fact]
    public void RecursiveClosureAndConditional()
    {
        Module m = Exec(
            "def make_counter():\n" +
            "    state = [0]\n" +
            "    def inc():\n" +
            "        state[0] = state[0] + 1\n" +
            "        return state[0]\n" +
            "    return inc\n" +
            "c = make_counter()\n" +
            "a = c()\n" +
            "b = c()\n",
            out _);
        Assert.Equal(StarlarkInt.Of(1), m.GetGlobal("a"));
        Assert.Equal(StarlarkInt.Of(2), m.GetGlobal("b"));
    }
}
