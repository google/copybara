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
