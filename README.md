sbt-dependency-graph
====================

Visualize your project's dependencies.

Preliminaries
-------------

Starting with version 0.8.0, the plugin will only work for sbt-projects that use sbt >= 0.13.8. See
the [0.7 branch](https://github.com/jrudolph/sbt-dependency-graph/tree/0.7) for older versions that still work with sbt < 0.13.8.

How To Use
----------

Since sbt-dependency-graph is an informational tool rather than one that changes your build, you will more than likely wish to
install it as a [global plugin] so that you can use it in any SBT project without the need to explicitly add it to each one. To do
this, add the plugin dependency to `~/.sbt/0.13/plugins/plugins.sbt`:

```scala
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.8.0")
```

This plugin is an auto-plugin which will be automatically enabled.

To add the plugin only to a single project, put this line into `project/plugins.sbt` of your project, instead.

Main Tasks
----------

 * `dependencyTree`: Shows an ASCII tree representation of the project's dependencies
 * `dependencyBrowseGraph`: Opens a browser window with a visualization of the dependency graph (courtesy of graphlib-dot + dagre-d3).
 * `dependencyGraph`: Shows an ASCII graph of the project's dependencies on the sbt console
 * `whatDependsOn <organization> <module> <revision>`: Find out what depends on an artifact. Shows a reverse dependency
   tree for the selected module.
 * `dependencyLicenseInfo`: show dependencies grouped by declared license
 * `dependencyGraphMl`: Generates a `.graphml` file with the project's dependencies to `target/dependencies-<config>.graphml`.
   Use e.g. [yEd](http://www.yworks.com/en/products_yed_about.html) to format the graph to your needs.
 * `dependencyDot`: Generates a .dot file with the project's dependencies to `target/dependencies-<config>.dot`.
    Use [graphviz](http://www.graphviz.org/) to render it to your preferred graphic format.
 * `ivyReport`: let's ivy generate the resolution report for you project. Use
   `show ivyReport` for the filename of the generated report

All tasks can be scoped to a configuration to get the report for a specific configuration. `test:dependencyGraph`,
for example, prints the dependencies in the `test` configuration. If you don't specify any configuration, `compile` is
assumed as usual.


Configuration settings
----------------------
 
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

Standalone usage
----------------

You can use the project without sbt as well by either depending on the library and calling
`IvyGraphMLDependencies.saveAsGraphML(IvyGraphMLDependencies.graph(reportFile), outputFile)` or by just getting the binary
and calling it like `scala sbt-dependency-graph-0.7.5.jar <ivy-report-xml-path> <target-path>`.

Inner Workings
--------------

sbt/Ivy's `update` task create ivy-report xml-files inside `.ivy2/cache` (in sbt 0.12.1:
`<project-dir>/target/resolution-cache/reports/<project-id>`). You can
just open them with your browser to look at the dependency report for your project.
This project takes the report xml of your project and creates a graphml file out of it. (BTW,
ivy can create graphml files itself, but since I didn't want to spend to much time getting
sbt to call into Ivy to create graphs, I went with the easy way here)

Known issues
------------

 * #19: There's an unfixed bug with graph generation for particular layouts. Workaround:
   Use `dependency-tree` instead of `dependency-graph`.

License
-------

Published under the [Apache License 2.0](http://en.wikipedia.org/wiki/Apache_license).

[global plugin]: http://www.scala-sbt.org/0.13/tutorial/Using-Plugins.html#Global+plugins
[global build configuration]: http://www.scala-sbt.org/0.13/docs/Global-Settings.html
