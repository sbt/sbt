# sbt-dependency-graph

[![Join the chat at https://gitter.im/jrudolph/sbt-dependency-graph](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/jrudolph/sbt-dependency-graph?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

Visualize your project's dependencies.

**Note: Under sbt >= 1.3.x some features might currently not work as expected or not at all (like `dependencyLicenses`).**

## Usage Instructions

sbt-dependency-graph is an informational tool rather than one that changes your build, so you will more than likely wish to
install it as a [global plugin] so that you can use it in any SBT project without the need to explicitly add it to each one. To do
this, add the plugin dependency to `~/.sbt/0.13/plugins/plugins.sbt` for sbt 0.13 or `~/.sbt/1.0/plugins/plugins.sbt` for sbt 1.0:

```scala
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.10.0-RC1")
```

To add the plugin only to a single project, put this line into `project/plugins.sbt` of your project, instead.

The plugin currently supports sbt versions >= 0.13.10 and sbt 1.0.x. For versions supporting older versions of sbt see
the notes of version [0.8.2](https://github.com/jrudolph/sbt-dependency-graph/tree/v0.8.2#compatibility-notes).

## Main Tasks

 * `dependencyTree`: Shows an ASCII tree representation of the project's dependencies
 * `dependencyBrowseGraph`: Opens a browser window with a visualization of the dependency graph (courtesy of graphlib-dot + dagre-d3).
 * `dependencyBrowseTree`: Opens a browser window with a visualization of the dependency tree (courtesy of jstree).
 * `dependencyList`: Shows a flat list of all transitive dependencies on the sbt console (sorted by organization and name)
 * `whatDependsOn <organization> <module> <revision>?`: Find out what depends on an artifact. Shows a reverse dependency
   tree for the selected module. The `<revision>` argument is optional.
 * `dependencyLicenseInfo`: show dependencies grouped by declared license
 * `dependencyStats`: Shows a table with each module a row with (transitive) Jar sizes and number of dependencies
 * `dependencyGraphMl`: Generates a `.graphml` file with the project's dependencies to `target/dependencies-<config>.graphml`.
   Use e.g. [yEd](http://www.yworks.com/en/products_yed_about.html) to format the graph to your needs.
 * `dependencyDot`: Generates a .dot file with the project's dependencies to `target/dependencies-<config>.dot`.
    Use [graphviz](http://www.graphviz.org/) to render it to your preferred graphic format.
 * `dependencyGraph`: Shows an ASCII graph of the project's dependencies on the sbt console (only supported on sbt 0.13)
 * `ivyReport`: Lets ivy generate the resolution report for you project. Use
   `show ivyReport` for the filename of the generated report

The following tasks also support the `toFile` subtask to save the contents to a file:

 * `dependencyTree`
 * `dependencyList`
 * `dependencyStats`
 * `dependencyLicenseInfo`

The `toFile` subtask has the following syntax:

```
<config>:<task>::toFile <filename> [-f|--force]
```

Use `-f` to force overwriting an existing file.

E.g. `test:dependencyStats::toFile target/depstats.txt` will write the output of the `dependencyStats` in the `test`
configuration to the file `target/depstats.txt` but would not overwrite an existing file.

All tasks can be scoped to a configuration to get the report for a specific configuration. `test:dependencyGraph`,
for example, prints the dependencies in the `test` configuration. If you don't specify any configuration, `compile` is
assumed as usual.

Note: If you want to run tasks with parameters from outside the sbt shell, make sure to put the whole task invocation in
quotes,  e.g. `sbt "whatDependsOn <org> <module> <version>"`.

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

## License

Published under the [Apache License 2.0](http://en.wikipedia.org/wiki/Apache_license).

[global plugin]: http://www.scala-sbt.org/0.13/tutorial/Using-Plugins.html#Global+plugins
[global build configuration]: http://www.scala-sbt.org/0.13/docs/Global-Settings.html
[#19]: https://github.com/jrudolph/sbt-dependency-graph/issues/19
