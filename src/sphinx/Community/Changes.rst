=======
Changes
=======

0.12.2 to 0.12.3
~~~~~~~~~~~~~~~~

- Allow ``cleanKeepFiles`` to contain directories
- Disable Ivy debug-level logging for performance. (gh-635)
- Invalidate artifacts not recorded in the original metadata when a module marked as changing changes. (gh-637, gh-641)
- Ivy Artifact needs wildcard configuration added if no explicit ones are defined. (gh-439)
- Right precedence of sbt.boot.properties lookup, handle qualifier correctly. (gh-651)
- Mark the tests failed exception as having already provided feedback.
- Handle exceptions not caught by the test framework when forking. (gh-653)
- Support ``reload plugins`` after ignoring a failure to load a project.
- Workaround for os deadlock detection at the process level. (gh-650)
- Fix for dependency on class file corresponding to a package. (Grzegorz K., gh-620)
- Fix incremental compilation problem with package objects inheriting from invalidated sources in a subpackage.
- Use Ivy's default name for the resolution report so that links to other configurations work.
- Include jars from java.ext.dirs in incremental classpath. (gh-678)
- Multi-line prompt text offset issue (Jibbers42, gh-625)
- Added ``xml:space="preserve"`` attribute to extraDependencyAttributes XML Block for publishing poms for plugins dependent on other plugins (Brendan M., gh-645)
- Tag the actual test task and not a later task.  (gh-692)
- Make exclude-classifiers per-user instead of per-build. (gh-634)
- Load global plugins in their own class loader and replace the base loader with that. (gh-272)
- Demote the default conflict warnings to the debug level.  These will be removed completely in 0.13. (gh-709)
- Fix Ivy cache issues when multiple resolvers are involed. (gh-704)


0.12.1 to 0.12.2
~~~~~~~~~~~~~~~~

- Support -Yrangepos. (Lex S., gh-607)
- Only make one call to test frameworks per test name.  (gh-520)
- Add ``-cp`` option to the ``apply`` method to make adding commands from an external program easier.
- Stable representation of refinement typerefs.  This fixes unnecessary recompilations in some cases. (Adriaan M., gh-610)
- Disable aggregation for ``run-main``. (gh-606)
- Concurrent restrictions: Untagged should be set based on the task's tags, not the tags of all tasks.
- When preserving the last modified time of files, convert negative values to 0
- Use ``java.lang.Throwable.setStackTrace`` when sending exceptions back from forked tests. (Eugene V., gh-543)
- Don't merge dependencies with mismatched transitive/force/changing values. (gh-582)
- Filter out null parent files when deleting empty directories. (Eugene V., gh-589)
- Work around File constructor not accepting URIs for UNC paths.  (gh-564)
- Split ForkTests react() out to workaround SI-6526 (avoids a stackoverflow in some forked test situations)
- Maven-style ivy repo support in the launcher config (Eric B., gh-585)
- Compare external binaries with canonical files (nau, gh-584)
- Call System.exit after the main thread is finished. (Eugene V., gh-565)
- Abort running tests on the first failure to communicate results back to the main process. (Eugene V., gh-557)
- Don't let the right side of the alias command fail the parse.  (gh-572)
- Don't try to look up the class file for a package.  (gh-620)

0.12.0 to 0.12.1
~~~~~~~~~~~~~~~~

Dependency management fixes:

-  Merge multiple dependency definitions for the same ID. Workaround for gh-468, gh-285, gh-419, gh-480.
-  Don't write section of pom if scope is 'compile'.
-  Ability to properly match on artifact type. Fixes gh-507 (Thomas).
-  Force ``update`` to run on changes to last modified time of artifacts
   or cached descriptor (part of fix for gh-532). It may also fix
   issues when working with multiple local projects via 'publish-local'
   and binary dependencies.
-  Per-project resolution cache that deletes cached files before
   ``update``. Notes:

  -  The resolution cache differs from the repository cache and does not
     contain dependency metadata or artifacts.
  -  The resolution cache contains the generated ivy files, properties,
     and resolve reports for the project.
  -  There will no longer be individual files directly in ``~/.ivy2/cache/``
  -  Resolve reports are now in ``target/resolution-cache/reports/``, viewable with a browser.
  -  Cache location includes extra attributes so that cross builds of a
     plugin do not overwrite each other. Fixes gh-532.
  
Three stage incremental compilation:

-  As before, the first step recompiles sources that were edited (or
   otherwise directly invalidated).
-  The second step recompiles sources from the first step whose API has
   changed, their direct dependencies, and sources forming a cycle with
   these sources.
-  The third step recompiles transitive dependencies of sources from the
   second step whose API changed.
-  Code relying mainly on composition should see decreased compilation
   times with this approach.
-  Code with deep inheritance hierarchies and large cycles between
   sources may take longer to compile.
-  ``last compile`` will show cycles that were processed in step 2.
   Reducing large cycles of sources shown here may decrease compile
   times.

Miscellaneous fixes and improvements:

-  Various test forking fixes. Fixes gh-512, gh-515.
-  Proper isolation of build definition classes. Fixes gh-536, gh-511.
-  ``orbit`` packaging should be handled like a standard jar. Fixes gh-499.
-  In ``IO.copyFile``, limit maximum size transferred via NIO. Fixes gh-491.
-  Add OSX JNI library extension in ``includeFilter`` by default. Fixes gh-500. (Indrajit)
-  Translate ``show x y`` into ``;show x ;show y`` . Fixes gh-495.
-  Clean up temporary directory on exit. Fixes gh-502.
-  ``set`` prints the scopes+keys it defines and affects.
-  Tab completion for ``set`` (experimental).
-  Report file name when an error occurs while opening a corrupt zip
   file in incremental compilation code. (James)
-  Defer opening logging output files until an actual write. Helps
   reduce number of open file descriptors.
-  Back all console loggers by a common console interface that merges
   (overwrites) consecutive ``Resolving xxxx ...`` lines when ansi codes
   are enabled (as first done by Play).

Forward-compatible-only change (not present in 0.12.0):

-  ``sourcesInBase`` setting controls whether sources in base directory
   are included. Fixes gh-494.

0.11.3 to 0.12.0
~~~~~~~~~~~~~~~~

The changes for 0.12.0 are listed on a separate page. See
:doc:`ChangeSummary_0.12.0`.

0.11.2 to 0.11.3
~~~~~~~~~~~~~~~~

Dropping scala-tools.org:

-  The sbt group ID is changed to ``org.scala-sbt`` (from
   org.scala-tools.sbt). This means you must use a 0.11.3 launcher to
   launch 0.11.3.
-  The convenience objects ``ScalaToolsReleases`` and
   ``ScalaToolsSnapshots`` now point to
   ``https://oss.sonatype.org/content/repositories/releases`` and
   ``.../snapshots``
-  The launcher no longer includes ``scala-tools.org`` repositories by
   default and instead uses the Sonatype OSS snapshots repository for
   Scala snapshots.
-  The ``scala-tools.org`` releases repository is no longer included as
   an application repository by default. The Sonatype OSS repository is
   *not* included by default in its place.

Other fixes:

-  Compiler interface works with 2.10
-  ``maxErrors`` setting is no longer ignored
-  Correct test count. gh-372 (Eugene)
-  Fix file descriptor leak in process library (Daniel)
-  Buffer url input stream returned by Using.  gh-437
-  Jsch version bumped to 0.1.46. gh-403
-  JUnit test detection handles ancestors properly (Indrajit)
-  Avoid unnecessarily re-resolving plugins. gh-368
-  Substitute variables in explicit version strings and custom
   repository definitions in launcher configuration
-  Support setting sbt.version from system property, which overrides
   setting in a properties file. gh-354
-  Minor improvements to command/key suggestions

0.11.1 to 0.11.2
~~~~~~~~~~~~~~~~

Notable behavior change:

-  The local Maven repository has been removed from the launcher's list
   of default repositories, which is used for obtaining sbt and Scala
   dependencies. This is motivated by the high probability that
   including this repository was causing the various problems some users
   have with the launcher not finding some dependencies (gh-217).

Fixes:

-  gh-257 Fix invalid classifiers in pom generation (Indrajit)
-  gh-255 Fix scripted plugin descriptor (Artyom)
-  Fix forking git on windows (Stefan, Josh)
-  gh-261 Fix whitespace handling for semicolon-separated commands
-  gh-263 Fix handling of dependencies with an explicit URL
-  gh-272 Show deprecation message for ``project/plugins/``

0.11.0 to 0.11.1
~~~~~~~~~~~~~~~~

Breaking change:

-  The scripted plugin is now in the ``sbt`` package so that it can be
   used from a named package

Notable behavior change:

-  By default, there is more logging during update: one line per
   dependency resolved and two lines per dependency downloaded. This is
   to address the appearance that sbt hangs on larger 'update's.

Fixes and improvements:

-  Show help for a key with ``help <key>``
-  gh-21 Reduced memory and time overhead of incremental recompilation with signature hash based
   approach.
-  Rotate global log so that only output since last prompt is displayed
   for ``last``
-  gh-169 Add support for exclusions with excludeAll and exclude methods on ModuleID. (Indrajit)
-  gh-235 Checksums configurable for launcher
-  gh-246 Invalidate ``update`` when ``update`` is invalidated for an internal project
   dependency
-  gh-138 Include plugin sources and docs in ``update-sbt-classifiers``
-  gh-219 Add cleanupCommands setting to specify commands to run before interpreter exits
-  gh-46 Fix regression in caching missing classifiers for ``update-classifiers`` and ``update-sbt-classifiers``.
-  gh-228 Set ``connectInput`` to true to connect standard input to forked run
-  gh-229 Limited task execution interruption using ctrl+c
-  gh-220 Properly record source dependencies from separate compilation runs in the same step.
-  gh-214 Better default behavior for classpathConfiguration for external Ivy files
-  gh-212 Fix transitive plugin dependencies.
-  gh-222 Generate section in make-pom. (Jan)
-  Build resolvers, loaders, and transformers.
-  Allow project dependencies to be modified by a setting (buildDependencies) but with the restriction that new builds cannot
   be introduced.
-  gh-174, gh-196, gh-201, gh-204, gh-207, gh-208, gh-226, gh-224, gh-253

0.10.1 to 0.11.0
~~~~~~~~~~~~~~~~

Major Improvements:

-  Move to 2.9.1 for project definitions and plugins
-  Drop support for 2.7
-  Settings overhaul, mainly to make API documentation more usable
-  Support using native libraries in ``run`` and ``test`` (but not
   ``console``, for example)
-  Automatic plugin cross-versioning. Use

   ::

       addSbtPlugin("group" % "name" % "version")

   in ``project/plugins.sbt`` instead of ``libraryDependencies += ...``
   See :doc:`/Extending/Plugins` for details

Fixes and Improvements:

-  Display all undefined settings at once, instead of only the first one
-  Deprecate separate ``classpathFilter``, ``defaultExcludes``, and
   ``sourceFilter`` keys in favor of ``includeFilter`` and
   ``excludeFilter`` explicitly scoped by ``unmanagedSources``,
   ``unmanagedResources``, or ``unmanagedJars`` as appropriate
   (Indrajit)
-  Default to using shared boot directory in ``~/.sbt/boot/``
-  Can put contents of ``project/plugins/`` directly in ``project/``
   instead. Will likely deprecate ``plugins/`` directory
-  Key display is context sensitive. For example, in a single project,
   the build and project axes will not be displayed
-  gh-114, gh-118, gh-121, gh-132, gh-135, gh-157: Various settings
   and error message improvements
-  gh-115: Support configuring checksums separately for ``publish`` and ``update``
-  gh-118: Add ``about`` command
-  gh-118, gh-131: Improve ``last`` command. Aggregate ``last <task>`` and display all recent output for ``last``
-  gh-120: Support read-only external file projects (Fred)
-  gh-128: Add ``skip`` setting to override recompilation change detection
-  gh-139: Improvements to pom generation (Indrajit)
-  gh-140, gh-145: Add standard manifest attributes to binary and source jars (Indrajit)
-  Allow sources used for ``doc`` generation to be different from sources for ``compile``
-  gh-156: Made ``package`` an alias for ``package-bin``
-  gh-162: handling of optional dependencies in pom generation

0.10.0 to 0.10.1
~~~~~~~~~~~~~~~~

Some of the more visible changes:

-  Support "provided" as a valid configuration for inter-project dependencies gh-53
-  Try out some better error messages for build.sbt in a few common situations gh-58
-  Drop "Incomplete tasks ..." line from error messages. gh-32
-  Better handling of javac logging. gc-74
-  Warn when reload discards session settings
-  Cache failing classifiers, making 'update-classifiers' a practical replacement for withSources()
-  Global settings may be provided in ~/.sbt/build.sbt gh-52
-  No need to define "sbtPlugin := true" in project/plugins/ or ~/.sbt/plugins/
-  Provide statistics and list of evicted modules in UpdateReport
-  Scope use of 'transitive-classifiers' by 'update-sbt-classifiers' and 'update-classifiers' for separate configuration.
-  Default project ID includes a hash of base directory to avoid collisions in simple cases.
-  'extra-loggers' setting to make it easier to add loggers
-  Associate ModuleID, Artifact and Configuration with a classpath entry
   (moduleID, artifact, and configuration keys). gh-41
-  Put httpclient on Ivy's classpath, which seems to speed up 'update'.

0.7.7 to 0.10.0
~~~~~~~~~~~~~~~

**Major redesign, only prominent changes listed.**

-  Project definitions in Scala 2.8.1
-  New configuration system: :doc:`/Examples/Quick-Configuration-Examples/`,
   :doc:`/Getting-Started/Full-Def`, and :doc:`/Getting-Started/Basic-Def/`
-  New task engine: :doc:`/Detailed-Topics/Tasks`
-  New multiple project support: :doc:`/Getting-Started/Full-Def`
-  More aggressive incremental recompilation for both Java and Scala sources
-  Merged plugins and processors into improved plugins system:
   :doc:`/Extending/Plugins`
-  `Web application <https://github.com/JamesEarlDouglas/xsbt-web-plugin>`_ and
   webstart support moved to plugins instead of core features
-  Fixed all of the issues in (Google Code) issue #44
-  Managed dependencies automatically updated when configuration changes
-  ``update-sbt-classifiers`` and ``update-classifiers`` tasks for
   retrieving sources and/or javadocs for dependencies, transitively
-  Improved artifact handling and configuration :doc:`/Detailed-Topics/Artifacts`
-  Tab completion parser combinators for commands and input tasks:
   :doc:`/Extending/Commands`
-  No project creation prompts anymore
-  Moved to GitHub: http://github.com/harrah/xsbt

0.7.5 to 0.7.7
~~~~~~~~~~~~~~

-  Workaround for Scala issue
   `#4426 <http://lampsvn.epfl.ch/trac/scala/ticket/4426>`_
-  Fix issue 156

0.7.4 to 0.7.5
~~~~~~~~~~~~~~

-  Joonas's update to work with Jetty 7.1 logging API changes.
-  Updated to work with Jetty 7.2 WebAppClassLoader binary
   incompatibility (issue 129).
-  Provide application and boot classpaths to tests and 'run'ning code
   according to http://gist.github.com/404272
-  Fix ``provided`` configuration. It is no longer included on the
   classpath of dependent projects.
-  Scala 2.8.1 is the default version used when starting a new project.
-  Updated to `Ivy 2.2.0 <http://ant.apache.org/ivy/history/2.2.0/release-notes.html>`_.
-  Trond's patches that allow configuring
   `jetty-env.xml <http://github.com/harrah/xsbt/commit/5e41a47f50e6>`_
   and
   `webdefault.xml <http://github.com/harrah/xsbt/commit/030e2ee91bac0>`_
-  Doug's `patch <http://github.com/harrah/xsbt/commit/aa75ecf7055db>`_
   to make 'projects' command show an asterisk next to current project
-  Fixed issue 122
-  Implemented issue 118
-  Patch from Viktor and Ross for issue 123
-  (RC1) Patch from Jorge for issue 100
-  (RC1) Fix ``<packaging>`` type

0.7.3 to 0.7.4
~~~~~~~~~~~~~~

-  prefix continuous compilation with run number for better feedback
   when logging level is 'warn'
-  Added ``pomIncludeRepository(repo: MavenRepository): Boolean`` that
   can be overridden to exclude local repositories by default
-  Added ``pomPostProcess(pom: Node): Node`` to make advanced
   manipulation of the default pom easier (``pomExtra`` already covers
   basic cases)
-  Added ``reset`` command to reset JLine terminal. This needs to be run
   after suspending and then resuming sbt.
-  Installer plugin is now a proper subproject of sbt.
-  Plugins can now only be Scala sources. BND should be usable in a
   plugin now.
-  More accurate detection of invalid test names. Invalid test names now
   generate an error and prevent the test action from running instead of
   just logging a warning.
-  Fix issue with using 2.8.0.RC1 compiler in tests.
-  Precompile compiler interface against 2.8.0.RC2
-  Add ``consoleOptions`` for specifying options to the console. It
   defaults to ``compileOptions``.
-  Properly support sftp/ssh repositories using key-based
   authentication. See the updated section of the :doc:`/Detailed-Topics/Resolvers` page.
-  ``def ivyUpdateLogging = UpdateLogging.DownloadOnly | Full | Quiet``.
   Default is ``DownloadOnly``. ``Full`` will log metadata resolution
   and provide a final summary.
-  ``offline`` property for disabling checking for newer dynamic
   revisions (like ``-SNAPSHOT``). This allows working offline with
   remote snapshots. Not honored for plugins yet.
-  History commands: ``!!, !?string, !-n, !n, !string, !:n, !:`` Run
   ``!`` to see help.
-  New section in launcher configuration ``[ivy]`` with a single label
   ``cache-directory``. Specify this to change the cache location used
   by the launcher.
-  New label ``classifiers`` under ``[app]`` to specify classifiers of
   additional artifacts to retrieve for the application.
-  Honor ``-Xfatal-warnings`` option added to compiler in 2.8.0.RC2.
-  Make ``scaladocTask`` a ``fileTask`` so that it runs only when
   ``index.html`` is older than some input source.
-  Made it easier to create default ``test-*`` tasks with different
   options
-  Sort input source files for consistency, addressing scalac's issues
   with source file ordering.
-  Derive Java source file from name of class file when no
   ``SourceFile`` attribute is present in the class file. Improves
   tracking when ``-g:none`` option is used.
-  Fix ``FileUtilities.unzip`` to be tail-recursive again.

0.7.2 to 0.7.3
~~~~~~~~~~~~~~

-  Fixed issue with scala.library.jar not being on javac's classpath
-  Fixed buffered logging for parallel execution
-  Fixed ``test-*`` tab completion being permanently set on first
   completion
-  Works with Scala 2.8 trunk again.
-  Launcher: Maven local repository excluded when the Scala version is a
   snapshot. This should fix issues with out of date Scala snapshots.
-  The compiler interface is precompiled against common Scala versions
   (for this release, 2.7.7 and 2.8.0.Beta1).
-  Added ``PathFinder.distinct``
-  Running multiple commands at once at the interactive prompt is now
   supported. Prefix each command with ';'.
-  Run and return the output of a process as a String with ``!!`` or as
   a (blocking) ``Stream[String]`` with ``lines``.
-  Java tests + Annotation detection
-  Test frameworks can now specify annotation fingerprints. Specify the
   names of annotations and sbt discovers classes with the annotations
   on it or one of its methods. Use version 0.5 of the test-interface.
-  Detect subclasses and annotations in Java sources (really, their
   class files)
-  Discovered is new root of hierarchy representing discovered
   subclasses + annotations. ``TestDefinition`` no longer fulfills this
   role.
-  ``TestDefinition`` is modified to be name+\ ``Fingerprint`` and
   represents a runnable test. It need not be ``Discovered``, but could
   be file-based in the future, for example.
-  Replaced testDefinitionClassNames method with ``fingerprints`` in
   ``CompileConfiguration``.
-  Added foundAnnotation to ``AnalysisCallback``
-  Added ``Runner2``, ``Fingerprint``, ``AnnotationFingerprint``, and
   ``SubclassFingerprint`` to the test-interface. Existing test
   frameworks should still work. Implement ``Runner2`` to use
   fingerprints other than ``SubclassFingerprint``.

0.7.1 to 0.7.2
~~~~~~~~~~~~~~

-  ``Process.apply`` no longer uses ``CommandParser``. This should fix
   issues with the android-plugin.
-  Added ``sbt.impl.Arguments`` for parsing a command like a normal
   action (for ``Processor``\ s)
-  Arguments are passed to ``javac`` using an argument file (``@``)
-  Added ``webappUnmanaged: PathFinder`` method to
   ``DefaultWebProject``. Paths selected by this ``PathFinder`` will not
   be pruned by ``prepare-webapp`` and will not be packaged by
   ``package``. For example, to exclude the GAE datastore directory:
   ``scala   override def webappUnmanaged =     (temporaryWarPath / "WEB-INF" / "appengine-generated" ***)``
-  Added some String generation methods to ``PathFinder``: ``toString``
   for debugging and ``absString`` and ``relativeString`` for joining
   the absolute (relative) paths by the platform separator.
-  Made tab completors lazier to reduce startup time.
-  Fixed ``console-project`` for custom subprojects
-  ``Processor`` split into ``Processor``/``BasicProcessor``.
   ``Processor`` provides high level of integration with command
   processing. ``BasicProcessor`` operates on a ``Project`` but does not
   affect command processing.
-  Can now use ``Launcher`` externally, including launching ``sbt``
   outside of the official jar. This means a ``Project`` can now be
   created from tests.
-  Works with Scala 2.8 trunk
-  Fixed logging level behavior on subprojects.
-  All sbt code is now at http://github.com/harrah/xsbt in one project.

0.7.0 to 0.7.1
~~~~~~~~~~~~~~

-  Fixed Jetty 7 support to work with JRebel
-  Fixed make-pom to generate valid dependencies section

0.5.6 to 0.7.0
~~~~~~~~~~~~~~

-  Unifed batch and interactive commands. All commands that can be
   executed at interactive prompt can be run from the command line. To
   run commands and then enter interactive prompt, make the last command
   'shell'.
-  Properly track certain types of synthetic classes, such as for
   comprehension with >30 clauses, during compilation.
-  Jetty 7 support
-  Allow launcher in the project root directory or the ``lib``
   directory. The jar name must have the form\ ``'*sbt-launch*.jar'`` in
   order to be excluded from the classpath.
-  Stack trace detail can be controlled with ``'on'``, ``'off'``,
   ``'nosbt'``, or an integer level. ``'nosbt'`` means to show stack
   frames up to the first ``sbt`` method. An integer level denotes the
   number of frames to show for each cause. This feature is courtesty of
   Tony Sloane.
-  New action 'test-run' method that is analogous to 'run', but for test
   classes.
-  New action 'clean-plugins' task that clears built plugins (useful for
   plugin development).
-  Can provide commands from a file with new command: ``<filename``
-  Can provide commands over loopback interface with new command:
   ``<port``
-  Scala version handling has been completely redone.
-  The version of Scala used to run sbt (currently 2.7.7) is decoupled
   from the version used to build the project.
-  Changing between Scala versions on the fly is done with the command:
   ``++<version>``
-  Cross-building is quicker. The project definition does not need to be
   recompiled against each version in the cross-build anymore.
-  Scala versions are specified in a space-delimited list in the
   ``build.scala.versions`` property.
-  Dependency management:
-  ``make-pom`` task now uses custom pom generation code instead of
   Ivy's pom writer.
-  Basic support for writing out Maven-style repositories to the pom
-  Override the 'pomExtra' method to provide XML (``scala.xml.NodeSeq``)
   to insert directly into the generated pom.
-  Complete control over repositories is now possible by overriding
   ``ivyRepositories``.
-  The interface to Ivy can be used directly.
-  Test framework support is now done through a uniform test interface.
   Implications:
-  New versions of specs, ScalaCheck, and ScalaTest are supported as
   soon as they are released.
-  Support is better, since the test framework authors provide the
   implementation.
-  Arguments can be passed to the test framework. For example: {{{ >
   test-only your.test -- -a -b -c }}}
-  Can provide custom task start and end delimiters by defining the
   system properties ``sbt.start.delimiter`` and ``sbt.end.delimiter``.
-  Revamped launcher that can launch Scala applications, not just
   ``sbt``
-  Provide a configuration file to the launcher and it can download the
   application and its dependencies from a repository and run it.
-  sbt's configuration can be customized. For example,
-  The ``sbt`` version to use in projects can be fixed, instead of read
   from ``project/build.properties``.
-  The default values used to create a new project can be changed.
-  The repositories used to fetch ``sbt`` and its dependencies,
   including Scala, can be configured.
-  The location ``sbt`` is retrieved to is configurable. For example,
   ``/home/user/.ivy2/sbt/`` could be used instead of ``project/boot/``.

0.5.5 to 0.5.6
~~~~~~~~~~~~~~

-  Support specs specifications defined as classes
-  Fix specs support for 1.6
-  Support ScalaTest 1.0
-  Support ScalaCheck 1.6
-  Remove remaining uses of structural types

0.5.4 to 0.5.5
~~~~~~~~~~~~~~

-  Fixed problem with classifier support and the corresponding test
-  No longer need ``"->default"`` in configurations (automatically
   mapped).
-  Can specify a specific nightly of Scala 2.8 to use (for example:
   ``2.8.0-20090910.003346-+``)
-  Experimental support for searching for project
   (``-Dsbt.boot.search=none|only|root-first|nearest``)
-  Fix issue where last path component of local repository was dropped
   if it did not exist.
-  Added support for configuring repositories on a per-module basis.
-  Unified batch-style and interactive-style commands. All commands that
   were previously interactive-only should be available batch-style.
   'reboot' does not pick up changes to 'scala.version' properly,
   however.

0.5.2 to 0.5.4
~~~~~~~~~~~~~~

-  Many logging related changes and fixes. Added ``FilterLogger`` and
   cleaned up interaction between ``Logger``, scripted testing, and the
   builder projects. This included removing the ``recordingDepth`` hack
   from Logger. Logger buffering is now enabled/disabled per thread.
-  Fix ``compileOptions`` being fixed after the first compile
-  Minor fixes to output directory checking
-  Added ``defaultLoggingLevel`` method for setting the initial level of
   a project's ``Logger``
-  Cleaned up internal approach to adding extra default configurations
   like ``plugin``
-  Added ``syncPathsTask`` for synchronizing paths to a target directory
-  Allow multiple instances of Jetty (new ``jettyRunTasks`` can be
   defined with different ports)
-  ``jettyRunTask`` accepts configuration in a single configuration
   wrapper object instead of many parameters
-  Fix web application class loading (issue #35) by using
   ``jettyClasspath=testClasspath---jettyRunClasspath`` for loading
   Jetty. A better way would be to have a ``jetty`` configuration and
   have ``jettyClasspath=managedClasspath('jetty')``, but this maintains
   compatibility.
-  Copy resources to ``target/resources`` and ``target/test-resources``
   using ``copyResources`` and ``copyTestResources`` tasks. Properly
   include all resources in web applications and classpaths (issue #36).
   ``mainResources`` and ``testResources`` are now the definitive
   methods for getting resources.
-  Updated for 2.8 (``sbt`` now compiles against September 11, 2009
   nightly build of Scala)
-  Fixed issue with position of ``^`` in compile errors
-  Changed order of repositories (local, shared, Maven Central, user,
   Scala Tools)
-  Added Maven Central to resolvers used to find Scala library/compiler
   in launcher
-  Fixed problem that prevented detecting user-specified subclasses
-  Fixed exit code returned when exception thrown in main thread for
   ``TrapExit``
-  Added ``javap`` task to ``DefaultProject``. It has tab completion on
   compiled project classes and the run classpath is passed to ``javap``
   so that library classes are available. Examples:
   ``scala    > javap your.Clazz    > javap -c scala.List``
-  Added ``exec`` task. Mixin ``Exec`` to project definition to use.
   This forks the command following ``exec``. Examples:
   ``scala    > exec echo Hi    > exec find src/main/scala -iname *.scala -exec wc -l {} ;``
-  Added ``sh`` task for users with a unix-style shell available (runs
   ``/bin/sh -c <arguments>``). Mixin ``Exec`` to project definition to
   use. Example:
   ``scala    > sh find src/main/scala -iname *.scala | xargs cat | wc -l``
-  Proper dependency graph actions (previously was an unsupported
   prototype): ``graph-src`` and ``graph-pkg`` for source dependency
   graph and quasi-package dependency graph (based on source directories
   and source dependencies)
-  Improved Ivy-related code to not load unnecessary default settings
-  Fixed issue #39 (sources were not relative in src package)
-  Implemented issue #38 (``InstallProject`` with 'install' task)
-  Vesa's patch for configuring the output of forked Scala/Java and
   processes
-  Don't buffer logging of forked ``run`` by default
-  Check ``Project.terminateWatch`` to determine if triggered execution
   should stop for a given keypress.
-  Terminate triggered execution only on 'enter' by default (previously,
   any keypress stopped it)
-  Fixed issue #41 (parent project should not declare jar artifact)
-  Fixed issue #42 (search parent directories for ``ivysettings.xml``)
-  Added support for extra attributes with Ivy. Use
   ``extra(key -> value)`` on ``ModuleIDs`` and ``Artifacts``. To define
   for a project's ID:
   ``scala   override def projectID = super.projectID extra(key -> value)``
   To specify in a dependency:
   ``scala   val dep = normalID extra(key -> value)``

0.5.1 to 0.5.2
~~~~~~~~~~~~~~

-  Fixed problem where dependencies of ``sbt`` plugins were not on the
   compile classpath
-  Added ``execTask`` that runs an ``sbt.ProcessBuilder`` when invoked
-  Added implicit conversion from ``scala.xml.Elem`` to
   ``sbt.ProcessBuilder`` that takes the element's text content, trims
   it, and splits it around whitespace to obtain the command.
-  Processes can now redirect standard input (see run with Boolean
   argument or !< operator on ``ProcessBuilder``), off by default
-  Made scripted framework a plugin and scripted tests now go in
   ``src/sbt-test`` by default
-  Can define and use an sbt test framework extension in a project
-  Fixed ``run`` action swallowing exceptions
-  Fixed tab completion for method tasks for multi-project builds
-  Check that tasks in ``compoundTask`` do not reference static tasks
-  Make ``toString`` of ``Path``\ s in subprojects relative to root
   project directory
-  ``crossScalaVersions`` is now inherited from parent if not specified
-  Added ``scala-library.jar`` to the ``javac`` classpath
-  Project dependencies are added to published ``ivy.xml``
-  Added dependency tracking for Java sources using classfile parsing
   (with the usual limitations)
-  Added ``Process.cat`` that will send contents of ``URL``\ s and
   ``File``\ s to standard output. Alternatively, ``cat`` can be used on
   a single ``URL`` or ``File``. Example:
   ``scala     import java.net.URL     import java.io.File     val spde = new URL("http://technically.us/spde/About")     val dispatch = new URL("http://databinder.net/dispatch/About")     val build = new File("project/build.properties")     cat(spde, dispatch, build) #| "grep -i scala" !``

0.4.6 to 0.5/0.5.1
~~~~~~~~~~~~~~~~~~

-  Fixed ``ScalaTest`` framework dropping stack traces
-  Publish only public configurations by default
-  Loader now adds ``.m2/repository`` for downloading Scala jars
-  Can now fork the compiler and runner and the runner can use a
   different working directory.
-  Maximum compiler errors shown is now configurable
-  Fixed rebuilding and republishing released versions of ``sbt``
   against new Scala versions (attempt #2)
-  Fixed snapshot reversion handling (Ivy needs changing pattern set on
   cache, apparently)
-  Fixed handling of default configuration when
   ``useMavenConfiguration`` is ``true``
-  Cleanup on Environment, Analysis, Conditional, ``MapUtilities``, and
   more...
-  Tests for Environment, source dependencies, library dependency
   management, and more...
-  Dependency management and multiple Scala versions
-  Experimental plugin for producing project bootstrapper in a
   self-extracting jar
-  Added ability to directly specify ``URL`` to use for dependency with
   the ``from(url: URL)`` method defined on ``ModuleID``
-  Fixed issue #30
-  Support cross-building with ``+`` when running batch actions
-  Additional flattening for project definitions: sources can go either
   in ``project/build/src`` (recursively) or ``project/build`` (flat)
-  Fixed manual ``reboot`` not changing the version of Scala when it is
   manually ``set``
-  Fixed tab completion for cross-building
-  Fixed a class loading issue with web applications

0.4.5 to 0.4.6
~~~~~~~~~~~~~~

-  Publishing to ssh/sftp/filesystem repository supported
-  Exception traces are printed by default
-  Fixed warning message about no ``Class-Path`` attribute from showing
   up for ``run``
-  Fixed ``package-project`` operation
-  Fixed ``Path.fromFile``
-  Fixed issue with external process output being lost when sent to a
   ``BufferedLogger`` with ``parallelExecution`` enabled.
-  Preserve history across ``clean``
-  Fixed issue with making relative path in jar with wrong separator
-  Added cross-build functionality (prefix action with ``+``).
-  Added methods ``scalaLibraryJar`` and ``scalaCompilerJar`` to
   ``FileUtilities``
-  Include project dependencies for ``deliver``/``publish``
-  Add Scala dependencies for ``make-pom``/``deliver``/``publish``,
   which requires these to depend on ``package``
-  Properly add compiler jar to run/test classpaths when main sources
   depend on it
-  ``TestFramework`` root ``ClassLoader`` filters compiler classes used
   by ``sbt``, which is required for projects using the compiler.
-  Better access to dependencies:
-  ``mainDependencies`` and ``testDependencies`` provide an analysis of
   the dependencies of your code as determined during compilation
-  ``scalaJars`` is deprecated, use ``mainDependencies.scalaJars``
   instead (provides a ``PathFinder``, which is generally more useful)
-  Added ``jettyPort`` method to ``DefaultWebProject``.
-  Fixed ``package-project`` to exclude ``project/boot`` and
   ``project/build/target``
-  Support specs 1.5.0 for Scala 2.7.4 version.
-  Parallelization at the subtask level
-  Parallel test execution at the suite/specification level.

0.4.3 to 0.4.5
~~~~~~~~~~~~~~

-  Sorted out repository situation in loader
-  Added support for ``http_proxy`` environment variable
-  Added ``download`` method from Nathan to ``FileUtilities`` to
   retrieve the contents of a URL.
-  Added special support for compiler plugins, see CompilerPlugins page.
-  ``reload`` command in scripted tests will now properly handle
   success/failure
-  Very basic support for Java sources: Java sources under
   ``src/main/java`` and ``src/test/java`` will be compiled.
-  ``parallelExecution`` defaults to value in parent project if there is
   one.
-  Added 'console-project' that enters the Scala interpreter with the
   current ``Project`` bound to the variable ``project``.
-  The default Ivy cache manager is now configured with
   ``useOrigin=true`` so that it doesn't cache artifacts from the local
   filesystem.
-  For users building from trunk, if a project specifies a version of
   ``sbt`` that ends in ``-SNAPSHOT``, the loader will update ``sbt``
   every time it starts up. The trunk version of ``sbt`` will always end
   in ``-SNAPSHOT`` now.
-  Added automatic detection of classes with main methods for use when
   ``mainClass`` is not explicitly specified in the project definition.
   If exactly one main class is detected, it is used for ``run`` and
   ``package``. If multiple main classes are detected, the user is
   prompted for which one to use for ``run``. For ``package``, no
   ``Main-Class`` attribute is automatically added and a warning is
   printed.
-  Updated build to cross-compile against Scala 2.7.4.
-  Fixed ``proguard`` task in ``sbt``'s project definition
-  Added ``manifestClassPath`` method that accepts the value for the
   ``Class-Path`` attribute
-  Added ``PackageOption`` called ``ManifestAttributes`` that accepts
   ``(java.util.jar.Attributes.Name, String)`` or ``(String, String)``
   pairs and adds them to the main manifest attributes
-  Fixed some situations where characters would not be echoed at prompts
   other than main prompt.
-  Fixed issue #20 (use ``http_proxy`` environment variable)
-  Implemented issue #21 (native process wrapper)
-  Fixed issue #22 (rebuilding and republishing released versions of
   ``sbt`` against new Scala versions, specifically Scala 2.7.4)
-  Implemented issue #23 (inherit inline repositories declared in parent
   project)

0.4 to 0.4.3
~~~~~~~~~~~~

-  Direct dependencies on Scala libraries are checked for version
   equality with ``scala.version``
-  Transitive dependencies on ``scala-library`` and ``scala-compiler``
   are filtered
-  They are fixed by ``scala.version`` and provided on the classpath by
   ``sbt``
-  To access them, use the ``scalaJars`` method,
   ``classOf[ScalaObject].getProtectionDomain.getCodeSource``, or
   mainCompileConditional.analysis.allExternals
-  The configurations checked/filtered as described above are
   configurable. Nonstandard configurations are not checked by default.
-  Version of ``sbt`` and Scala printed on startup
-  Launcher asks if you want to try a different version if ``sbt`` or
   Scala could not be retrieved.
-  After changing ``scala.version`` or ``sbt.version`` with ``set``,
   note is printed that ``reboot`` is required.
-  Moved managed dependency actions to ``BasicManagedProject``
   (``update`` is now available on ``ParentProject``)
-  Cleaned up ``sbt``'s build so that you just need to do ``update`` and
   ``full-build`` to build from source. The trunk version of ``sbt``
   will be available for use from the loader.
-  The loader is now a subproject.
-  For development, you'll still want the usual actions (such as
   ``package``) for the main builder and ``proguard`` to build the
   loader.
-  Fixed analysis plugin improperly including traits/abstract classes in
   subclass search
-  ``ScalaProject``\ s already had everything required to be parent
   projects: flipped the switch to enable it
-  Proper method task support in scripted tests (``package`` group tests
   rightly pass again)
-  Improved tests in loader that check that all necessary libraries were
   downloaded properly

0.3.7 to 0.4
~~~~~~~~~~~~

-  Fixed issue with ``build.properties`` being unnecessarily updated in
   sub-projects when loading.
-  Added method to compute the SHA-1 hash of a ``String``
-  Added pack200 methods
-  Added initial process interface
-  Added initial webstart support
-  Added gzip methods
-  Added ``sleep`` and ``newer`` commands to scripted testing.
-  Scripted tests now test the version of ``sbt`` being built instead of
   the version doing the building.
-  ``testResources`` is put on the test classpath instead of
   ``testResourcesPath``
-  Added ``jetty-restart``, which does ``jetty-stop`` and then
   ``jetty-run``
-  Added automatic reloading of default web application
-  Changed packaging behaviors (still likely to change)
-  Inline configurations now allowed (can be used with configurations in
   inline XML)
-  Split out some code related to managed dependencies from
   ``BasicScalaProject`` to new class ``BasicManagedProject``
-  Can specify that maven-like configurations should be automatically
   declared
-  Fixed problem with nested modules being detected as tests
-  ``testResources``, ``integrationTestResources``, and
   ``mainResources`` should now be added to appropriate classpaths
-  Added project organization as a property that defaults to inheriting
   from the parent project.
-  Project creation now prompts for the organization.
-  Added method tasks, which are top-level actions with parameters.
-  Made ``help``, ``actions``, and ``methods`` commands available to
   batch-style invocation.
-  Applied Mikko's two fixes for webstart and fixed problem with
   pack200+sign. Also, fixed nonstandard behavior when gzip enabled.
-  Added ``control`` method to ``Logger`` for action lifecycle logging
-  Made standard logging level convenience methods final
-  Made ``BufferedLogger`` have a per-actor buffer instead of a global
   buffer
-  Added a ``SynchronizedLogger`` and a ``MultiLogger`` (intended to be
   used with the yet unwritten ``FileLogger``)
-  Changed method of atomic logging to be a method ``logAll`` accepting
   ``List[LogEvent]`` instead of ``doSynchronized``
-  Improved action lifecycle logging
-  Parallel logging now provides immediate feedback about starting an
   action
-  General cleanup, including removing unused classes and methods and
   reducing dependencies between classes
-  ``run`` is now a method task that accepts options to pass to the
   ``main`` method (``runOptions`` has been removed, ``runTask`` is no
   longer interactive, and ``run`` no longer starts a console if
   ``mainClass`` is undefined)
-  Major task execution changes:
-  Tasks automatically have implicit dependencies on tasks with the same
   name in dependent projects
-  Implicit dependencies on interactive tasks are ignored, explicit
   dependencies produce an error
-  Interactive tasks must be executed directly on the project on which
   they are defined
-  Method tasks accept input arguments (``Array[String]``) and
   dynamically create the task to run
-  Tasks can depend on tasks in other projects
-  Tasks are run in parallel breadth-first style
-  Added ``test-only`` method task, which restricts the tests to run to
   only those passed as arguments.
-  Added ``test-failed`` method task, which restricts the tests to run.
   First, only tests passed as arguments are run. If no tests are
   passed, no filtering is done. Then, only tests that failed the
   previous run are run.
-  Added ``test-quick`` method task, which restricts the tests to run.
   First, only tests passed as arguments are run. If no tests are
   passed, no filtering is done. Then, only tests that failed the
   previous run or had a dependency change are run.
-  Added launcher that allows declaring version of sbt/scala to build
   project with.
-  Added tab completion with ~
-  Added basic tab completion for method tasks, including ``test-*``
-  Changed default pack options to be the default options of
   Pack200.Packer
-  Fixed ~ behavior when action doesn't exist

0.3.6 to 0.3.7
~~~~~~~~~~~~~~

-  Improved classpath methods
-  Refactored various features into separate project traits
-  ``ParentProject`` can now specify dependencies
-  Support for ``optional`` scope
-  More API documentation
-  Test resource paths provided on classpath for testing
-  Added some missing read methods in ``FileUtilities``
-  Added scripted test framework
-  Change detection using hashes of files
-  Fixed problem with manifests not being generated (bug #14)
-  Fixed issue with scala-tools repository not being included by default
   (again)
-  Added option to set ivy cache location (mainly for testing)
-  trace is no longer a logging level but a flag enabling/disabling
   stack traces
-  Project.loadProject and related methods now accept a Logger to use
-  Made hidden files and files that start with ``'.'`` excluded by
   default (``'.*'`` is required because subversion seems to not mark
   ``.svn`` directories hidden on Windows)
-  Implemented exit codes
-  Added continuous compilation command ``cc``

0.3.5 to 0.3.6
~~~~~~~~~~~~~~

-  Fixed bug #12.
-  Compiled with 2.7.2.

0.3.2 to 0.3.5
~~~~~~~~~~~~~~

-  Fixed bug #11.
-  Fixed problem with dependencies where source jars would be used
   instead of binary jars.
-  Fixed scala-tools not being used by default for inline
   configurations.
-  Small dependency management error message correction
-  Slight refactoring for specifying whether scala-tools releases gets
   added to configured resolvers
-  Separated repository/dependency overriding so that repositories can
   be specified inline for use with ``ivy.xml`` or ``pom.xml`` files
-  Added ability to specify Ivy XML configuration in Scala.
-  Added ``clean-cache`` action for deleting Ivy's cache
-  Some initial work towards accessing a resource directory from tests
-  Initial tests for ``Path``
-  Some additional ``FileUtilities`` methods, some ``FileUtilities``
   method adjustments and some initial tests for ``FileUtilities``
-  A basic framework for testing ``ReflectUtilities``, not run by
   default because of run time
-  Minor cleanup to ``Path`` and added non-empty check to path
   components
-  Catch additional exceptions in ``TestFramework``
-  Added ``copyTask`` task creation method.
-  Added ``jetty-run`` action and added ability to package war files.
-  Added ``jetty-stop`` action.
-  Added ``console-quick`` action that is the same as ``console`` but
   doesn't compile sources first.
-  Moved some custom ``ClassLoader``\ s to ``ClasspathUtilities`` and
   improved a check.
-  Added ability to specify hooks to call before ``sbt`` shuts down.
-  Added ``zip``, ``unzip`` methods to ``FileUtilities``
-  Added ``append`` equivalents to ``write*`` methods in
   ``FileUtilites``
-  Added first draft of integration testing
-  Added batch command ``compile-stats``
-  Added methods to create tasks that have basic conditional execution
   based on declared sources/products of the task
-  Added ``newerThan`` and ``olderThan`` methods to ``Path``
-  Added ``reload`` action to reread the project definition without
   losing the performance benefits of an already running jvm
-  Added ``help`` action to tab completion
-  Added handling of (effectively empty) scala source files that create
   no class files: they are always interpreted as modified.
-  Added prompt to retry project loading if compilation fails
-  ``package`` action now uses ``fileTask`` so that it only executes if
   files are out of date
-  fixed ``ScalaTest`` framework wrapper so that it fails the ``test``
   action if tests fail
-  Inline dependencies can now specify configurations

0.3.1 to 0.3.2
~~~~~~~~~~~~~~

-  Compiled jar with Java 1.5.

0.3 to 0.3.1
~~~~~~~~~~~~

-  Fixed bugs #8, #9, and #10.

0.2.3 to 0.3
~~~~~~~~~~~~

-  Version change only for first release.

0.2.2 to 0.2.3
~~~~~~~~~~~~~~

-  Added tests for ``Dag``, ``NameFilter``, ``Version``
-  Fixed handling of trailing ``*``\ s in ``GlobFilter`` and added some
   error-checking for control characters, which ``Pattern`` doesn't seem
   to like
-  Fixed ``Analysis.allProducts`` implementation
-  It previously returned the sources instead of the generated classes
-  Will only affect the count of classes (it should be correct now) and
   the debugging of missed classes (erroneously listed classes as
   missed)
-  Made some implied preconditions on ``BasicVersion`` and
   ``OpaqueVersion`` explicit
-  Made increment version behavior in ``ScalaProject`` easier to
   overload
-  Added ``Seq[..Option]`` alternative to ``...Option*`` for tasks
-  Documentation generation fixed to use latest value of version
-  Fixed ``BasicVersion.incrementMicro``
-  Fixed test class loading so that ``sbt`` can test the version of
   ``sbt`` being developed (previously, the classes from the executing
   version of ``sbt`` were tested)

0.2.1 to 0.2.2
~~~~~~~~~~~~~~

-  Package name is now a call-by-name parameter for the package action
-  Fixed release action calling compile multiple times

0.2.0 to 0.2.1
~~~~~~~~~~~~~~

-  Added some action descriptions
-  jar name now comes from normalized name (lowercased and spaces to
   dashes)
-  Some cleanups related to creating filters
-  Path should only 'get' itself if the underlying file exists to be
   consistent with other ``PathFinders``
-  Added ``---`` operator for ``PathFinder`` that excludes paths from
   the ``PathFinder`` argument
-  Removed ``***`` operator on ``PathFinder``
-  ``**`` operator on ``PathFinder`` matches all descendents or self
   that match the ``NameFilter`` argument
-  The above should fix bug ``#6``
-  Added version increment and release actions.
-  Can now build sbt with sbt. Build scripts ``build`` and ``clean``
   will still exist.

0.1.9 to 0.2.0
~~~~~~~~~~~~~~

-  Implemented typed properties and access to system properties
-  Renamed ``metadata`` directory to ``project``
-  Information previously in ``info`` file now obtained by properties:
-  ``info.name --> name``
-  ``info.currentVersion --> version``
-  Concrete ``Project`` subclasses should have a constructor that
   accepts a single argument of type ``ProjectInfo`` (argument
   ``dependencies: Iterable[Project]`` has been merged into
   ``ProjectInfo``)

0.1.8 to 0.1.9
~~~~~~~~~~~~~~

-  Better default implementation of ``allSources``.
-  Generate warning if two jars on classpath have the same name.
-  Upgraded to specs 1.4.0
-  Upgraded to ``ScalaCheck`` 1.5
-  Changed some update options to be final vals instead of objects.
-  Added some more API documentation.
-  Removed release action.
-  Split compilation into separate main and test compilations.
-  A failure in a ``ScalaTest`` run now fails the test action.
-  Implemented reporters for ``compile/scaladoc``, ``ScalaTest``,
   ``ScalaCheck``, and ``specs`` that delegate to the appropriate
   ``sbt.Logger``.

0.1.7 to 0.1.8
~~~~~~~~~~~~~~

-  Improved configuring of tests to exclude.
-  Simplified version handling.
-  Task ``&&`` operator properly handles dependencies of tasks it
   combines.
-  Changed method of inline library dependency declarations to be
   simpler.
-  Better handling of errors in parallel execution.

0.1.6 to 0.1.7
~~~~~~~~~~~~~~

-  Added graph action to generate dot files (for graphiz) from
   dependency information (work in progress).
-  Options are now passed to tasks as varargs.
-  Redesigned ``Path`` properly, including ``PathFinder`` returning a
   ``Set[Path]`` now instead of ``Iterable[Path]``.
-  Moved paths out of ``ScalaProject`` and into ``BasicProjectPaths`` to
   keep path definitions separate from task definitions.
-  Added initial support for managing third-party libraries through the
   ``update`` task, which must be explicitly called (it is not a
   dependency of compile or any other task). This is experimental,
   undocumented, and known to be incomplete.
-  Parallel execution implementation at the project level, disabled by
   default. To enable, add:
   ``scala  override def parallelExecution = true`` to your project
   definition. In order for logging to make sense, all project logging
   is buffered until the project is finished executing. Still to be done
   is some sort of notification of project execution (which ones are
   currently executing, how many remain)
-  ``run`` and ``console`` are now specified as "interactive" actions,
   which means they are only executed on the project in which they are
   defined when called directly, and not on all dependencies. Their
   dependencies are still run on dependent projects.
-  Generalized conditional tasks a bit. Of note is that analysis is no
   longer required to be in metadata/analysis, but is now in
   target/analysis by default.
-  Message now displayed when project definition is recompiled on
   startup
-  Project no longer inherits from Logger, but now has a log member.
-  Dependencies passed to ``project`` are checked for null (may help
   with errors related to initialization/circular dependencies)
-  Task dependencies are checked for null
-  Projects in a multi-project configuration are checked to ensure that
   output paths are different (check can be disabled)
-  Made ``update`` task globally synchronized because Ivy is not
   thread-safe.
-  Generalized test framework, directly invoking frameworks now (used
   reflection before).
-  Moved license files to licenses/
-  Added support for ``specs`` and some support for ``ScalaTest`` (the
   test action doesn't fail if ``ScalaTest`` tests fail).
-  Added ``specs``, ``ScalaCheck``, ``ScalaTest`` jars to lib/
-  These are now required for compilation, but are optional at runtime.
-  Added the appropriate licenses and notices.
-  Options for ``update`` action are now taken from updateOptions
   member.
-  Fixed ``SbtManager`` inline dependency manager to work properly.
-  Improved Ivy configuration handling (not compiled with test
   dependencies yet though).
-  Added case class implementation of ``SbtManager`` called
   ``SimpleManager``.
-  Project definitions not specifying dependencies can now use just a
   single argument constructor.

0.1.5 to 0.1.6
~~~~~~~~~~~~~~

-  ``run`` and ``console`` handle ``System.exit`` and multiple threads
   in user code under certain circumstances (see RunningProjectCode).

0.1.4 to 0.1.5
~~~~~~~~~~~~~~

-  Generalized interface with plugin (see ``AnalysisCallback``)
-  Split out task implementations and paths from ``Project`` to
   ``ScalaProject``
-  Subproject support (changed required project constructor signature:
   see ``sbt/DefaultProject.scala``)
-  Can specify dependencies between projects
-  Execute tasks across multiple projects
-  Classpath of all dependencies included when compiling
-  Proper inter-project source dependency handling
-  Can change to a project in an interactive session to work only on
   that project (and its dependencies)
-  External dependency handling
-  Tracks non-source dependencies (compiled classes and jars)
-  Requires each class to be provided by exactly one classpath element
   (This means you cannot have two versions of the same class on the
   classpath, e.g. from two versions of a library)
-  Changes in a project propagate the right source recompilations in
   dependent projects
-  Consequences:
-  Recompilation when changing java/scala version
-  Recompilation when upgrading libraries (again, as indicated in the
   second point, situations where you have library-1.0.jar and
   library-2.0.jar on the classpath at the same time are not handled
   predictably. Replacing library-1.0.jar with library-2.0.jar should
   work as expected.)
-  Changing sbt version will recompile project definitions

0.1.3 to 0.1.4
~~~~~~~~~~~~~~

-  Autodetection of Project definitions.
-  Simple tab completion/history in an interactive session with JLine
-  Added descriptions for most actions

0.1.2 to 0.1.3
~~~~~~~~~~~~~~

-  Dependency management between tasks and auto-discovery tasks.
-  Should work on Windows.

0.1.1 to 0.1.2
~~~~~~~~~~~~~~

-  Should compile/build on Java 1.5
-  Fixed run action implementation to include scala library on classpath
-  Made project configuration easier

0.1 to 0.1.1
~~~~~~~~~~~~

-  Fixed handling of source files without a package
-  Added easy project setup

