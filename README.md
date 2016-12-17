# sbt-dependency-graph

[![Join the chat at https://gitter.im/jrudolph/sbt-dependency-graph](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/jrudolph/sbt-dependency-graph?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

Visualize your project's dependencies.

## Preliminaries

The plugin works best with sbt >= 0.13.6. See the [compatibility notes](#compatibility-notes) to use this plugin with an older version of sbt.

## Usage Instructions

Since sbt-dependency-graph is an informational tool rather than one that changes your build, you will more than likely wish to
install it as a [global plugin] so that you can use it in any SBT project without the need to explicitly add it to each one. To do
this, add the plugin dependency to `~/.sbt/0.13/plugins/plugins.sbt`:

```scala
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.8.2")
```

To add the plugin only to a single project, put this line into `project/plugins.sbt` of your project, instead.

This plugin is an auto-plugin which will be automatically enabled starting from sbt 0.13.5.

## Main Tasks

 * `dependencyTree`: Shows an ASCII tree representation of the project's dependencies
 * `dependencyBrowseGraph`: Opens a browser window with a visualization of the dependency graph (courtesy of graphlib-dot + dagre-d3).
 * `dependencyGraph`: Shows an ASCII graph of the project's dependencies on the sbt console
 * `dependencyList`: Shows a flat list of all transitive dependencies on the sbt console (sorted by organization and name)
 * `whatDependsOn <organization> <module> <revision>`: Find out what depends on an artifact. Shows a reverse dependency
   tree for the selected module.
 * `dependencyLicenseInfo`: show dependencies grouped by declared license
 * `dependencyStats`: Shows a table with each module a row with (transitive) Jar sizes and number of dependencies
 * `dependencyGraphMl`: Generates a `.graphml` file with the project's dependencies to `target/dependencies-<config>.graphml`.
   Use e.g. [yEd](http://www.yworks.com/en/products_yed_about.html) to format the graph to your needs.
 * `dependencyDot`: Generates a .dot file with the project's dependencies to `target/dependencies-<config>.dot`.
    Use [graphviz](http://www.graphviz.org/) to render it to your preferred graphic format.
 * `ivyReport`: let's ivy generate the resolution report for you project. Use
   `show ivyReport` for the filename of the generated report

All tasks can be scoped to a configuration to get the report for a specific configuration. `test:dependencyGraph`,
for example, prints the dependencies in the `test` configuration. If you don't specify any configuration, `compile` is
assumed as usual.

## Configuration settings

 * `filterScalaLibrary`: Defines if the scala library should be excluded from the output of the dependency-* functions.
   If `true`, instead of showing the dependency `"[S]"` is appended to the artifact name. Set to `false` if
   you want the scala-library dependency to appear in the output. (default: true)
 * `dependencyGraphMLFile`: a setting which allows configuring the output path of `dependency-graph-ml`.
 * `dependencyDotFile`: a setting which allows configuring the output path of `dependency-dot`.
 * `dependencyDotHeader`: a setting to customize the header of the dot file (e.g. to set your preferred node shapes).
 * `dependencyDotNodeLabel`: defines the format of a node label
   (default set to `[organisation]<BR/><B>[name]</B><BR/>[version]`)

E.g. in `build.sbt` you can change configuration settings like this:

```scala
filterScalaLibrary := false // include scala library in output

dependencyDotFile := file("dependencies.dot") //render dot file to `./dependencies.dot`
```

## Known issues

 * [#19]: There's an unfixed bug with graph generation for particular layouts. Workaround:
   Use `dependency-tree` instead of `dependency-graph`.
 * [#39]: When using sbt-dependency-graph with sbt < 0.13.6.

## Compatibility notes

 * sbt < 0.13.6: The plugin will fall back on the old ivy report XML backend which suffers from [#39].
 * sbt < 0.13.5: Old versions of sbt have no `AutoPlugin` support, you need to add

   ```scala
net.virtualvoid.sbt.graph.DependencyGraphSettings.graphSettings
   ```
   to your `build.sbt` or (`~/.sbt/0.13/user.sbt` for global configuration) to enable the plugin.
 * sbt <= 0.12.x: Old versions of sbt are not actively supported any more. Please use the old version from the [0.7 branch](https://github.com/jrudolph/sbt-dependency-graph/tree/0.7).


## License

Published under the [Apache License 2.0](http://en.wikipedia.org/wiki/Apache_license).

[global plugin]: http://www.scala-sbt.org/0.13/tutorial/Using-Plugins.html#Global+plugins
[global build configuration]: http://www.scala-sbt.org/0.13/docs/Global-Settings.html
[#19]: https://github.com/jrudolph/sbt-dependency-graph/issues/19
[#39]: https://github.com/jrudolph/sbt-dependency-graph/issues/39
