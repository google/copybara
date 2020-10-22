# Copyright 2016 Google Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Bazel rule to generate copybara reference docs."""

def _doc_impl(ctx):
    jars = []
    for dep in ctx.attr.deps:
        for jar in dep[JavaInfo].transitive_source_jars.to_list():
            jars.append(jar)
    tmp = ctx.actions.declare_file("tmp.md")
    ctx.actions.run(
        tools = [ctx.executable._doc_tool],
        inputs = jars,
        outputs = [tmp, ctx.outputs.class_list],
        progress_message = "Generating reference documentation for %s" % ctx.label,
        use_default_shell_env = True,
        executable = ctx.executable._doc_tool.path,
        arguments = [tmp.path, ctx.outputs.class_list.path] + [f.path for f in jars],
    )

    # If suffix file exists, concat, copy otherwise
    if ctx.attr.template_file != None:
        ctx.actions.run_shell(
            inputs = [ctx.files.template_file[0], tmp],
            outputs = [ctx.outputs.out],
            progress_message = "Appending suffix from %s" % ctx.files.template_file,
            command = "sed -e '/<!-- Generated reference here -->/r./%s' %s >> %s" % (tmp.path, ctx.files.template_file[0].path, ctx.outputs.out.path),
        )
    else:
        ctx.actions.run(
            inputs = [tmp],
            outputs = [ctx.outputs.out],
            executable = "/bin/cp",
            arguments = [tmp.path, ctx.outputs.out.path],
        )

# Generates documentation by scanning the transitive set of dependencies of a Java binary.
doc_generator = rule(
    attrs = {
        "deps": attr.label_list(allow_rules = [
            "java_binary",
            "java_library",
        ]),
        "_doc_tool": attr.label(
            executable = True,
            cfg = "host",
            allow_files = True,
            default = Label("//java/com/google/copybara:doc_skylark.sh"),
        ),
        "template_file": attr.label(mandatory = False, allow_single_file = True),
    },
    outputs = {"out": "%{name}.md", "class_list": "%{name}_class_list.txt"},
    implementation = _doc_impl,
)
