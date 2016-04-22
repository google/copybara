def cram_test(name, srcs, deps=[]):
    for s in srcs:
        testname = name + '_' + s
        script = testname + '_script.sh'
        gr = "_gen_" + script
        # This genrule is a kludge, and at some point we should move
        # to a real rule
        # (http://bazel.io/docs/skylark/rules.html). What this does is
        # it builds a "bash script" whose first line execs cram on the
        # script. Conveniently, that first line is a comment as far as
        # cram is concerned (it's not indented at all), so everything
        # just works.
        native.genrule(name=gr,
                       srcs=[s],
                       outs=[script],
                       cmd=("echo 'exec $${TEST_SRCDIR}/copybara/integration/cram " +
                            "--xunit-file=$$XML_OUTPUT_FILE $$0' > \"$@\" ; " +
                            "cat $(SRCS) >> \"$@\""),
        )

        native.sh_test(name=testname,
                       srcs=[script],
                       data=["//copybara/integration:cram"] + deps,
        )
