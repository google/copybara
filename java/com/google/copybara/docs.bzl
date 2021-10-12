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

def _doc_generator_impl(ctx):
    jars = []
    for target in ctx.attr.targets:
        for jar in target[JavaInfo].transitive_source_jars.to_list():
            jars.append(jar)

    ctx.actions.run(
        inputs = jars + ctx.files.template_file,
        outputs = [ctx.outputs.out],
        executable = ctx.executable.generator,
        arguments = [
            ",".join([j.path for j in jars]),
            ctx.outputs.out.path,
        ] + [f.path for f in ctx.files.template_file],
    )

# Generates documentation by scanning the transitive set of dependencies of a Java binary.
doc_generator = rule(
    attrs = {
        "targets": attr.label_list(allow_rules = [
            "java_binary",
            "java_library",
        ]),
        "generator": attr.label(
            executable = True,
            cfg = "exec",
            mandatory = True,
        ),
        "template_file": attr.label(mandatory = False, allow_single_file = True),
        "out": attr.output(mandatory = True),
    },
    implementation = _doc_generator_impl,
    output_to_genfiles = True,
)

def copybara_reference(name, *, out, libraries, template_file = None):
    """
    Auto-generate reference documentation for a target containing Copybara libraries.

    out: Name of the output file to generate.
    libraries: List of libraries for which to generate reference documentation.
    template_file: Optional template file in which to insert the generated reference.
    """
    native.java_binary(
        name = "generator",
        main_class = "com.google.copybara.doc.Generator",
        runtime_deps = ["//java/com/google/copybara/doc:generator-lib"] + libraries,
    )

    doc_generator(
        name = name,
        out = out,
        generator = ":generator",
        targets = libraries,
        template_file = template_file,
    )
