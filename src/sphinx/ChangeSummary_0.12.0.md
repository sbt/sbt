[#274]: https://github.com/harrah/xsbt/pull/274
[#304]: https://github.com/harrah/xsbt/issues/304
[#315]: https://github.com/harrah/xsbt/issues/315
[#327]: https://github.com/harrah/xsbt/issues/327
[#335]: https://github.com/harrah/xsbt/issues/335
[#361]: https://github.com/harrah/xsbt/issues/361
[#393]: https://github.com/harrah/xsbt/issues/393
[#396]: https://github.com/harrah/xsbt/issues/396
[#380]: https://github.com/harrah/xsbt/issues/380
[#389]: https://github.com/harrah/xsbt/issues/389
[#388]: https://github.com/harrah/xsbt/issues/388
[#387]: https://github.com/harrah/xsbt/issues/387
[#386]: https://github.com/harrah/xsbt/issues/386
[#378]: https://github.com/harrah/xsbt/issues/378
[#377]: https://github.com/harrah/xsbt/issues/377
[#368]: https://github.com/harrah/xsbt/issues/368
[#394]: https://github.com/harrah/xsbt/issues/394
[#369]: https://github.com/harrah/xsbt/issues/369
[#403]: https://github.com/harrah/xsbt/issues/403
[#412]: https://github.com/harrah/xsbt/issues/412
[#415]: https://github.com/harrah/xsbt/issues/415
[#420]: https://github.com/harrah/xsbt/issues/420
[#462]: https://github.com/harrah/xsbt/pull/462
[#472]: https://github.com/harrah/xsbt/pull/472
[Launcher]: https://github.com/harrah/xsbt/wiki/Launcher

# 0.12.0 Changes

## Features, fixes, changes with compatibility implications (incomplete, please help)

 * The cross versioning convention has changed for Scala versions 2.10 and later as well as for sbt plugins.
 * When invoked directly, 'update' will always perform an update ([#335])
 * The sbt plugins repository is added by default for plugins and plugin definitions. [#380]
 * Plugin configuration directory precedence has changed (see details section below)
 * Source dependencies have been fixed, but the fix required changes (see details section below)
 * Aggregation has changed to be more flexible (see details section below)
 * Task axis syntax has changed from key(for task) to task::key (see details section below)
 * The organization for sbt has to changed to `org.scala-sbt` (was: org.scala-tools.sbt).  This affects users of the scripted plugin in particular.
 * `artifactName` type has changed to `(ScalaVersion, Artifact, ModuleID) => String`
 * `javacOptions` is now a task
 * `session save` overwrites settings in `build.sbt` (when appropriate). [#369]
 * scala-library.jar is now required to be on the classpath in order to compile Scala code.  See the `scala-library.jar` section at the bottom of the page for details.

## Features

 * Support for forking tests ([#415])
 * `test-quick` (see details section below)
 * Support globally overriding repositories ([#472]).
 * Added `print-warnings` task that will print unchecked and deprecation warnings from the previous compilation without needing to recompile (Scala 2.10+ only)
 * Support for loading an ivy settings file from a URL.
 * `projects add/remove <URI>` for temporarily working with other builds
 * Enhanced control over parallel execution (see details section below)
 * `inspect tree <key>` for calling `inspect` command recursively ([#274])

## Fixes

 * Delete a symlink and not its contents when recursively deleting a directory.
 * Fix detection of ancestors for java sources
 * Fix the resolvers used for `update-sbt-classifiers` ([#304])
 * Fix auto-imports of plugins ([#412]) 
 * Argument quoting (see details section below)
 * Properly reset JLine after being stopped by Ctrl+z (unix only). [#394]

## Improvements

 * The launcher can launch all released sbt versions back to 0.7.0.
 * A more refined hint to run 'last' is given when a stack trace is suppressed.
 * Use java 7 Redirect.INHERIT to inherit input stream of subprocess ([#462],[#327]).  This should fix issues when forking interactive programs. (@vigdorchik)
 * Mirror ivy 'force' attribute ([#361]) 
 * Various improvements to `help` and `tasks` commands as well as new `settings` command ([#315])
 * Bump jsch version to 0.1.46. ([#403])
 * Improved help commands: `help`, `tasks`, `settings`.
 * Bump to JLine 1.0 (see details section below)
 * Global repository setting (see details section below)
 * Other fixes/improvements: [#368], [#377], [#378], [#386], [#387], [#388], [#389]

## Experimental or In-progress

 * API for embedding incremental compilation.  This interface is subject to change, but already being used in [a branch of the scala-maven-plugin](https://github.com/davidB/scala-maven-plugin/tree/feature/sbt-inc).
 * Experimental support for keeping the Scala compiler resident.  Enable by passing `-Dsbt.resident.limit=n` to sbt, where `n` is an integer indicating the maximum number of compilers to keep around.
 * The [Howto pages](http://www.scala-sbt.org/howto.html) on the [new site](http://www.scala-sbt.org) are at least readable now.  There is more content to write and more formatting improvements are needed, so [pull requests are welcome](https://github.com/sbt/sbt.github.com).

## Details of major changes from 0.11.2 to 0.12.0

## Plugin configuration directory

In 0.11.0, plugin configuration moved from `project/plugins/` to just `project/`, with `project/plugins/` being deprecated.  Only 0.11.2 had a deprecation message, but in all of 0.11.x, the presence of the old style `project/plugins/` directory took precedence over the new style.  In 0.12.0, the new style takes precedence.  Support for the old style won't be removed until 0.13.0.

  1. Ideally, a project should ensure there is never a conflict.  Both styles are still supported; only the behavior when there is a conflict has changed.  
  2. In practice, switching from an older branch of a project to a new branch would often leave an empty `project/plugins/` directory that would cause the old style to be used, despite there being no configuration there.
  3. Therefore, the intention is that this change is strictly an improvement for projects transitioning to the new style and isn't noticed by other projects.

## Parsing task axis

There is an important change related to parsing the task axis for settings and tasks that fixes [#202](https://github.com/harrah/xsbt/issues/202)

  1. The syntax before 0.12 has been `{build}project/config:key(for task)`
  2. The proposed (and implemented) change for 0.12 is `{build}project/config:task::key`
  3. By moving the task axis before the key, it allows for easier discovery (via tab completion) of keys in plugins.
  4. It is not planned to support the old syntax.

## Aggregation

Aggregation has been made more flexible.  This is along the direction that has been previously discussed on the mailing list.

  1. Before 0.12, a setting was parsed according to the current project and only the exact setting parsed was aggregated.
  2. Also, tab completion did not account for aggregation.
  3. This meant that if the setting/task didn't exist on the current project, parsing failed even if an aggregated project contained the setting/task.
  4. Additionally, if compile:package existed for the current project, *:package existed for an aggregated project, and the user requested 'package' to run (without specifying the configuration), *:package wouldn't be run on the aggregated project (because it isn't the same as the compile:package key that existed on the current project).
  5. In 0.12, both of these situations result in the aggregated settings being selected.  For example,
    1. Consider a project `root` that aggregates a subproject `sub`.
    2. `root` defines `*:package`.
    3. `sub` defines `compile:package` and `compile:compile`.
    4. Running `root/package` will run `root/*:package` and `sub/compile:package`
    5. Running `root/compile` will run `sub/compile:compile`
  6. This change was made possible in part by the change to task axis parsing.

## Parallel Execution

Fine control over parallel execution is supported as described here: https://github.com/harrah/xsbt/wiki/Parallel-Execution

  1. The default behavior should be the same as before, including the `parallelExecution` settings.
  2. The new capabilities of the system should otherwise be considered experimental.
  3. Therefore, `parallelExecution` won't be deprecated at this time.

## Source dependencies

A fix for issue [#329](https://github.com/harrah/xsbt/issues/329) is included in 0.12.0.  This fix ensures that only one version of a plugin is loaded across all projects.  There are two parts to this.

  1. The version of a plugin is fixed by the first build to load it.  In particular, the plugin version used in the root build (the one in which sbt is started in) always overrides the version used in dependencies.
  2. Plugins from all builds are loaded in the same class loader.

Additionally, Sanjin's patches to add support for hg and svn URIs are included.

  1. sbt uses subversion to retrieve URIs beginning with `svn` or `svn+ssh`.  An optional fragment identifies a specific revision to checkout.
  2. Because a URI for mercurial doesn't have a mercurial-specific scheme, sbt requires the URI to be prefixed with `hg:` to identify it as a mercurial repository.
  3. Also, URIs that end with `.git` are now handled properly.

## Cross building

The cross version suffix is shortened to only include the major and minor version for Scala versions starting with the 2.10 series and for sbt versions starting with the 0.12 series.  For example, `sbinary_2.10` for a normal library or `sbt-plugin_2.10_0.12` for an sbt plugin.  This requires forward and backward binary compatibility across incremental releases for both Scala and sbt.

  1. This change has been a long time coming, but it requires everyone publishing an open source project to switch to 0.12 to publish for 2.10 or adjust the cross versioned prefix in their builds appropriately.
  2. Obviously, using 0.12 to publish a library for 2.10 requires 0.12.0 to be released before projects publish for 2.10.
  3. There is now the concept of a binary version.  This is a subset of the full version string that represents binary compatibility.  That is, equal binary versions implies binary compatibility.  All Scala versions prior to 2.10 use the full version for the binary version to reflect previous sbt behavior.  For 2.10 and later, the binary version is `<major>.<minor>`.
  4. The cross version behavior for published artifacts is configured by the crossVersion setting.  It can be configured for dependencies by using the `cross` method on `ModuleID` or by the traditional %% dependency construction variant.  By default, a dependency has cross versioning disabled when constructed with a single % and uses the binary Scala version when constructed with %%.
  5. The artifactName function now accepts a type ScalaVersion as its first argument instead of a String.  The full type is now `(ScalaVersion, ModuleID, Artifact) => String`.  ScalaVersion contains both the full Scala version (such as 2.10.0) as well as the binary Scala version (such as 2.10).
  6. The flexible version mapping added by Indrajit has been merged into the `cross` method and the %% variants accepting more than one argument have been deprecated.  See [[Cross Build]] for details.

## Global repository setting

 Define the repositories to use by putting a standalone `[repositories]` section (see the [Launcher] page) in `~/.sbt/repositories` and pass `-Dsbt.override.build.repos=true` to sbt.  Only the repositories in that file will be used by the launcher for retrieving sbt and Scala and by sbt when retrieving project dependencies.  (@jsuereth)

## test-quick

`test-quick` ([#393]) runs the tests specified as arguments (or all tests if no arguments are given) that:

  1. have not been run yet OR
  2. failed the last time they were run OR
  3. had any transitive dependencies recompiled since the last successful run
 
## Argument quoting

Argument quoting ([#396]) from the intereactive mode works like Scala string literals.

  1. `> command "arg with spaces,\n escapes interpreted"`
  2. `> command """arg with spaces,\n escapes not interpreted"""` 
  3. For the first variant, note that paths on Windows use backslashes and need to be escaped (`\\`).  Alternatively, use the second variant, which does not interpret escapes.
  4. For using either variant in batch mode, note that a shell will generally require the double quotes themselves to be escaped.

## scala-library.jar

sbt versions prior to 0.12.0 provided the location of scala-library.jar to scalac even if scala-library.jar wasn't on the classpath.  This allowed compiling Scala code without scala-library as a dependency, for example, but this was a misfeature.  Instead, the Scala library should be declared as `provided`:

```scala
// Don't automatically add the scala-library dependency
//  in the 'compile' configuration
autoScalaLibrary := false

libraryDependencies +=
  "org.scala-lang" % "scala-library" % "2.9.2" % "provided"
```