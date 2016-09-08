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

def _doc_impl(ctx):
  jars=[]
  for dep in ctx.attr.deps:
    for jar in dep.java.transitive_source_jars:
      jars.append(jar)
  ctx.action(
      inputs=jars,
      outputs=[ctx.outputs.out],
      progress_message="Generating documentation for %s" % ctx.label,
      command= "%s %s %s %s" %
        (ctx.executable._doc_tool.path,
         ctx.outputs.out.path,
         ",".join(ctx.attr.elements),
         " ".join([f.path for f in jars])),
  )

def _skylark_doc_impl(ctx):
  jars=[]
  for dep in ctx.attr.deps:
    for jar in dep.java.transitive_source_jars:
      jars.append(jar)
  ctx.action(
      inputs=jars,
      outputs=[ctx.outputs.out],
      progress_message="Generating Skylark documentation for %s" % ctx.label,
      command= "%s %s %s" %
               (ctx.executable._doc_tool.path,
                ctx.outputs.out.path,
                " ".join([f.path for f in jars])),
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
    },
    outputs = {"out": "%{name}.md"},
    implementation = _skylark_doc_impl,
)
