===========================
 Dependency Management Flow
===========================

0.12.1 addresses several issues with dependency management. These fixes
were made possible by specific, reproducible examples, such as a
situation where the resolution cache got out of date (gh-532). A brief summary of
the current work flow with dependency management in sbt follows.

Background
==========

:key:`update` resolves dependencies according to the settings in a build
file, such as :key:`libraryDependencies` and :key:`resolvers`. Other tasks use
the output of :key:`update` (an `UpdateReport`) to form various
classpaths. Tasks that in turn use these classpaths, such as :key:`compile`
or :key:`run`, thus indirectly depend on `update`. This means that before
:key:`compile` can run, the :key:`update` task needs to run. However,
resolving dependencies on every :key:`compile` would be unnecessarily slow
and so :key:`update` must be particular about when it actually performs a
resolution.

Caching and Configuration
=========================

1. Normally, if no dependency management configuration has changed since
   the last successful resolution and the retrieved files are still
   present, sbt does not ask Ivy to perform resolution.
2. Changing the configuration, such as adding or removing dependencies
   or changing the version or other attributes of a dependency, will
   automatically cause resolution to be performed. Updates to locally
   published dependencies should be detected in sbt 0.12.1 and later and
   will force an :key:`update`. Dependent tasks like :key:`compile` and
   :key:`run` will get updated classpaths.
3. Directly running the :key:`update` task (as opposed to a task that
   depends on it) will force resolution to run, whether or not
   configuration changed. This should be done in order to refresh remote
   SNAPSHOT dependencies.
4. When `offline := true`, remote SNAPSHOTs will not be updated by a
   resolution, even an explicitly requested :key:`update`. This should
   effectively support working without a connection to remote
   repositories. Reproducible examples demonstrating otherwise are
   appreciated. Obviously, :key:`update` must have successfully run before
   going offline.
5. Overriding all of the above, `skip in update := true` will tell sbt
   to never perform resolution. Note that this can cause dependent tasks
   to fail. For example, compilation may fail if jars have been deleted
   from the cache (and so needed classes are missing) or a dependency
   has been added (but will not be resolved because skip is true). Also,
   :key:`update` itself will immediately fail if resolution has not been
   allowed to run since the last :key:`clean`.

General troubleshooting steps
=============================

A. Run :key:`update` explicitly. This will typically fix problems with out
   of date SNAPSHOTs or locally published artifacts. 

B. If a file cannot be
   found, look at the output of :key:`update` to see where Ivy is looking for
   the file. This may help diagnose an incorrectly defined dependency or a
   dependency that is actually not present in a repository.
   
C. `last update` contains more information about the most recent
   resolution and download. The amount of debugging output from Ivy is
   high, so you may want to use `lastGrep` (run `help lastGrep` for
   usage).
   
D. Run :key:`clean` and then :key:`update`. If this works, it could
   indicate a bug in sbt, but the problem would need to be reproduced in
   order to diagnose and fix it.
   
E. Before deleting all of the Ivy cache,
   first try deleting files in `~/.ivy2/cache` related to problematic
   dependencies. For example, if there are problems with dependency
   `"org.example" % "demo" % "1.0"`, delete
   `~/.ivy2/cache/org.example/demo/1.0/` and retry :key:`update`. This
   avoids needing to redownload all dependencies.
   
F. Normal sbt usage
   should not require deleting files from `~/.ivy2/cache`, especially if
   the first four steps have been followed. If deleting the cache fixes a
   dependency management issue, please try to reproduce the issue and
   submit a test case.
   
Plugins
=======

These troubleshooting steps can be run for plugins by changing to the
build definition project, running the commands, and then returning to
the main project. For example:

.. code-block:: console

   > reload plugins
   > update
   > reload return

Notes
=====

A. Configure offline behavior for all projects on a machine by putting
   `offline := true` in `~/.sbt/global.sbt`. A command that does this
   for the user would make a nice pull request. Perhaps the setting of
   offline should go into the output of `about` or should it be a warning
   in the output of :key:`update` or both?
   
B. The cache improvements in 0.12.1 address issues in the change detection 
   for :key:`update` so that it will correctly re-resolve automatically in more
   situations. A problem with an out of date cache can usually be attributed
   to a bug in that change detection if explicitly running `update` fixes 
   the problem.
   
C. A common solution to dependency management problems in sbt has been to
   remove `~/.ivy2/cache`. Before doing this with 0.12.1, be sure to
   follow the steps in the troubleshooting section first. In particular,
   verify that a :key:`clean` and an explicit :key:`update` do not solve the
   issue.
   
D. There is no need to mark SNAPSHOT dependencies as `changing()`
   because sbt configures Ivy to know this already.
