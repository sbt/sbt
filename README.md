sbt-dependency-graph [![Build Status](https://buildhive.cloudbees.com/job/jrudolph/job/sbt-dependency-graph/badge/icon)](https://buildhive.cloudbees.com/job/jrudolph/job/sbt-dependency-graph/)
====================

Visualize your project's dependencies.

Requirements
------------

* Simple Build Tool

How To Use
----------

For sbt 0.11, add sbt-dependency-graph as a dependency in `project/plugins.sbt`:

```scala
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.6.0")
```

*Note*: The organization has recently been changed to `net.virtual-void`.

Then, add the following to your `<project-root>/build.sbt` (that's not `project/build.sbt`!) as a standalone line:

```scala
net.virtualvoid.sbt.graph.Plugin.graphSettings
```

or if you use the full configuration, add 

```scala
.settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*)
```

to your project definition.

Tasks & Settings
----------------

 * `dependency-graph`: Shows an ASCII graph of your dependency on the sbt console
 * `dependency-graph-ml`: Generates a .graphml file with your dependencies to `target/dependencies-<config>.graphml`.
   Use e.g. [yEd](http://www.yworks.com/en/products_yed_about.html) to format the graph to your needs.
 * `dependency-graph-ml-file`: a setting which allows configuring the output path of `dependency-graph-ml`.
 * `ivy-report`: let's ivy generate the resolution report for you project. Use
   `show ivy-report` for the filename of the generated report

All tasks can be scoped to a configuration to get the report for a specific configuration. `test:dependency-graph`,
for example, prints the dependencies in the `test` configuration. If you don't specify any configuration, `compile` is
assumed as usual.

Standalone usage
----------------

You can use the project without sbt as well by either depending on the library and calling
`IvyGraphMLDependencies.transfrom(sourceIvyReport, targetFile)` or by just getting the binary
and calling it like `scala sbt-dependency-graph-0.5.1.jar <ivy-report-xml-path> <target-path>`.


Inner Workings
--------------

sbt/Ivy's `update` task create ivy-report xml-files inside `.ivy2/cache`. You can
just open them with your browser to look at the dependency report for your project.
This project takes the report xml of your project and creates a graphml file out of it. (BTW,
ivy can create graphml files itself, but since I didn't want to spend to much time getting
sbt to call into Ivy to create graphs, I went with the easy way here)

License
-------

Copyright (c) 2011, 2012 Johannes Rudolph

Published under the [Apache License 2.0](http://en.wikipedia.org/wiki/Apache_license).