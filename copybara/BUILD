# Copyright 2016 Google Inc. All Rights Reserved.

licenses(["unencumbered"])  # Owned by Google. No external contributions


genrule(
    name = "docs",
    srcs = [
        "//java/com/google/copybara:libcopybara_lib-src.jar",
    ],
    outs = ["docs.md"],
    cmd = """
set -eu

# A very primitive documentation generation. We just append all the Markdown files.
declare -ar GENSRCS=($(SRCS))
for GENSRC in "$${GENSRCS[@]}"; do
  unzip -q -p "$${GENSRC}" '*.md' >> $(location docs.md)
done
""",
)
