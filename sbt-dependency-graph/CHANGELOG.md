# Changelog

## Unreleased
 * [#184](https://github.com/jrudolph/sbt-dependency-graph/pull/184): Fix regression in 0.10.0-RC1 for recent sbt versions when
   `cachedResolution` (with coursier turned off). Thanks [@bjaglin](https://github.com/bjaglin) for the report and the fix.

## Version 0.10.0-RC1 (2019-07-24)
 * [#136](https://github.com/jrudolph/sbt-dependency-graph/pull/136): Added `dependencyBrowseTree` to open a searchable dependency tree in the browser.
   Thanks, [@pcejrowski](https://github.com/pcejrowski) for contributing this feature.
 * [#163](https://github.com/jrudolph/sbt-dependency-graph/pull/163): Remove some usage of internal sbt APIs, this allows to run sbt-dependency-graph with sbt 1.3.0
   but results are not entirely correct.
 * [#165](https://github.com/jrudolph/sbt-dependency-graph/pull/165): For common operations introduce `asString`, `printToConsole`, and `toFile` subtasks

## Version 0.9.2 (2018-08-26)
 * [#159](https://github.com/jrudolph/sbt-dependency-graph/pull/159): Fixed regression in `whatDependsOn` where task parser failed when no other sbt-dependency-graph task was called before

## Version 0.9.1 (2018-07-17)

 * [#110](https://github.com/jrudolph/sbt-dependency-graph/issues/110): `whatDependsOn` can now be called without specifying a version. Thanks, @chikei for the initial implementation.
 * [#150](https://github.com/jrudolph/sbt-dependency-graph/issues/150): `ivyReport` now reports correct path again even for older sbt versions (< 0.13.16)

## Version 0.9.0 (2017-10-25)

This version (finally!) adds support for sbt 1.0. *sbt-dependency-graph* depends on a lot of internals from sbt to do its
work which is why it was quite an effort to do the migration. Thanks [@MasseGuillaume](https://github.com/MasseGuillaume) from Scala Center,
[@2m](https://github.com/2m), and [@xuwei-k](https://github.com/xuwei-k) for helping out with the effort.

The plugin is cross-built for sbt 0.13 (and will continued to be for while). The `dependencyGraph` task is currently not
supported on sbt 1.0. Use `dependencyBrowseGraph`, instead.

## Version 0.8.2 (2016-02-01)

This is a maintenance release [fixing](https://github.com/jrudolph/sbt-dependency-graph/issues/89) `dependencyBrowseGraph`
in the latest Chrome versions. Thanks [@chtefi](https://github.com/chtefi)!

## Version 0.8.1 (2016-01-08)

This is a maintenance release fixing a regression in 0.8.0 and adding two small features.

All changes:

 * [#84](https://github.com/jrudolph/sbt-dependency-graph/issues/84): Fix regression of DOT label rendering introduced in 0.8.0.
 * [#83](https://github.com/jrudolph/sbt-dependency-graph/issues/83): Added new task `dependencyStats` which prints a
   simple table of jar sizes for all your dependencies. Handy if you want to know why your assembled jar gets so big.
 * [#85](https://github.com/jrudolph/sbt-dependency-graph/issues/85): Added new task `dependencyList` which prints a
   flat, deduplicated list of all the transitive dependencies.

## Version 0.8.0 (2015-11-26)

sbt-dependency-graph is finally an AutoPlugin and can now show the dependency graph in the browser directly.

### New features

 - (experimental) open dependency graph directly in the browser with `dependencyBrowseGraph` ([#29](https://github.com/jrudolph/sbt-dependency-graph/issues/29))
   ![dependencyBrowseGraph in action](https://gist.githubusercontent.com/jrudolph/941754bcf67a0fafe495/raw/7d80d766feb7af6ba2a69494e1f3ceb1fd40d4da/Screenshot%2520from%25202015-11-26%252014:18:19.png)

 - this plugin is finally an sbt AutoPlugin and it is automatically enabled
   ([#51](https://github.com/jrudolph/sbt-dependency-graph/issues/51))

**Note: To update from 0.7.x remove the `net.virtualvoid.sbt.graph.Plugin.graphSettings` line from your configurations.**

### Other changes

 - a new backend was implemented which accesses the in-memory dependency data structures of sbt directly. The plugin doesn't
   require accessing the ivy report XML any more (the old backend can still be wired in for comparisons if needed) which
   should have solved the race condition and the dreaded `FileNotFoundException` ([#39](https://github.com/jrudolph/sbt-dependency-graph/issues/39))
   in multi-module projects. The new backend is only used for sbt >= 0.13.6.
 - code was restructured which touched a lot of the classes but didn't change the function or syntax of settings and tasks.
 - fixed [#77](https://github.com/jrudolph/sbt-dependency-graph/issues/77)


## Version 0.7.5 (2015-03-30)

This is a maintenance release adding support for sbt 0.13.8.

All changes:

 * [#67](https://github.com/jrudolph/sbt-dependency-graph/issues/67): Added support for sbt 0.13.8. Thanks
   [@eed3si9n](https://github.com/eed3si9n) for the fix.
 * [#37](https://github.com/jrudolph/sbt-dependency-graph/issues/37): Don't fail with StringIndexOutOfBoundsException
   for deep trees.
 * [#44](https://github.com/jrudolph/sbt-dependency-graph/issues/44): Only match scala lib by org/name.
   Thanks [@2beaucoup](https://github.com/2beaucoup) for the fix.

## Version 0.7.4 (2013-06-26)

This is a maintenance release fixing an exception when generating graphs without a terminal [#32](https://github.com/jrudolph/sbt-dependency-graph/issues/32).

## Version 0.7.3 (2013-04-28)

This is a maintenance release. Following issues have been fixed:

  * [#27](https://github.com/jrudolph/sbt-dependency-graph/issues/27): A dependency configured with
    a version range was not properly associated with its dependant.
  * [#30](https://github.com/jrudolph/sbt-dependency-graph/issues/30) & [#31](https://github.com/jrudolph/sbt-dependency-graph/issues/31):
    Make it work again with sbt 0.12.3. The path of the dependency resolution file changed in sbt 0.12.3.
    Thanks [ebowman](https://github.com/ebowman) for the fix.

## Version 0.7.2 (2013-03-02)

This is a maintenance release. Following issues have been fixed:

  * [#27](https://github.com/jrudolph/sbt-dependency-graph/issues/27): A dependency configured with
    a version range was not properly associated with its dependant.


## Version 0.7.1

New features in this version:

 * `dependency-license-info`: show dependencies grouped by declared license
 * `dependency-dot`: create dot file from dependency graph. Contributed by
    [berleon](https://github.com/berleon).

## Version 0.7.0 (2012-10-24)

New features in this version:

  * `dependency-graph` now renders a real graph. Thanks go to [Matt Russell](https://github.com/mdr/) for
    this added awesomeness.
  * The tree output from previous versions is now available with `dependency-tree`.
  * New task `what-depends-on` showing reverse dependency tree for a selected module (incl. tab-completion for modules)
  * Don't fail in cases of a missing dependency. Show errors directly in the output.
  * Show info about evicted versions.
  * By default, exclude scala-library dependency and append `[S]` to the artifact name instead. Set
    `filter-scala-library` to `false` to disable this feature.
  * Works with sbt 0.12.1. The ivy report files were moved to a new location making an update necessary.


## Version 0.6.0 (2012-05-23)

New features in this version:

  * `dependency-graph` task now prints the dependency graph to the console
    (contributed by @gseitz)
  * `dependency-graph-ml` contains now the old functionality of `dependency-graph`
    which generates a `.graphml` file. Nodes now contain the dependency version as well (contributed by @gseitz).
  * The output filename of `dependency-graph-ml` has been changed to include the configuration name. It is now
    configurable using the `dependency-graph-ml-file` setting.
  * The common `scalaVersion in update` idiom to support Scala 2.9.1 libraries in a
    Scala 2.9.2 broke the plugin in 0.5.2, because it wouldn't find the ivy report xml file
    any more. This was fixed.
  * All tasks are scoped by configuration.

## Version 0.5.2 (2012-02-13)

## Version 0.5.1 (2011-11-18)

## Version 0.5 (2011-11-15)