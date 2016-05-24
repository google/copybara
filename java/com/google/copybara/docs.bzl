def _doc_impl(ctx):
  jars=[]
  for dep in ctx.attr.deps:
    for jar in dep.java.transitive_source_jars:
      jars.append(jar)
  ctx.action(
      inputs=jars,
      outputs=[ctx.outputs.out],
      arguments=[ctx.outputs.out.path, ",".join(ctx.attr.elements)] + [f.path for f in jars],
      progress_message="Generating documentation for %s" % ctx.label,
      executable=ctx.executable._doc_tool)

# Generates documentation by scanning the transitive set of dependencies of a Java binary.
doc_generator = rule(
    attrs = {
        "elements": attr.string_list(default = [
            "Config",
            "Workflow",
            "Origin",
            "Destination",
            "Transformation",
        ]),
        "deps": attr.label_list(allow_rules=["java_binary", "java_library"]),
        "_doc_tool": attr.label(
            executable = True,
            allow_files = True,
            default = Label("//java/com/google/copybara:doc.sh"),
        ),
    },
    outputs = {"out": "%{name}.md"},
    implementation = _doc_impl,
)
