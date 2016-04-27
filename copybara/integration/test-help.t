  $ ${TEST_SRCDIR}/java/com/google/copybara/copybara --help
  Usage: copybara [options] CONFIG_PATH [SOURCE_REF]
    Options:
      --folder-dir
         Local directory to put the output of the transformation
      --gerrit-change-id
         ChangeId to use in the generated commit message
         Default: <empty string>
      --git-first-commit
         Ignore that the fetch reference doesn't exist when pushing to destination
         Default: false
      --git-previous-ref
         Previous SHA-1 reference used for the migration.
         Default: <empty string>
      --git-repo-storage
         Location of the storage path for git repositories
         Default: */.copybara/repos (glob)
      --help
         Shows this help text
         Default: false
      --work-dir
         Directory where all the transformations will be performed. By default a
         temporary directory.
      -v
         Verbose output.
         Default: false
  
  Example:
    copybara myproject.copybara origin/master

