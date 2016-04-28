from __future__ import absolute_import

import os
import shutil
import sys
import tempfile

def main(args):
    # paranoia: our script name is the same as the library we want
    # from an external repo, so delete the main in this module to
    # avoid accidental infinite recursion in case the imports bind the
    # wrong way. That shouldn't happen with the absolute_import change
    # above, but this saved a fair amount of confusion while setting up
    # this contraption.
    global main
    del main
    # Import the bogon cram from external
    from external import cram as fakecram
    # insert it into the start of sys.path
    sys.path.insert(0, os.path.dirname(fakecram.__file__))
    from cram import _main
    args = args[1:]
    unused_opts, paths, unused_getusage = _main._parseopts(args)
    td = tempfile.mkdtemp(dir=os.environ['TEST_TMPDIR'])
    for p in paths:
        dest = os.path.join(td, os.path.basename(p))
        shutil.copyfile(p, dest)
        for i, x in enumerate(args):
            if x == p:
                args[i] = dest
    # now we can import cram and be on our merry way
    import cram
    sys.exit(cram.main(args))

if __name__ == '__main__':
    main(sys.argv)
