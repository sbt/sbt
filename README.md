sbt-dependency-graph
====================

Create a graph (in graphml format) from your project's dependencies.

Requirements
------------

* Simple Build Tool

How To Use
----------

For sbt 0.11, add sbt-dependency-graph as a dependency in `project/plugins.sbt`:

```scala
addSbtPlugin("net.virtualvoid" % "sbt-dependency-graph" % "0.5.1")
```

or, alternatively, in `project/plugins/project/build.scala`:

```scala
import sbt._

object Plugins extends Build {
  lazy val root = Project("root", file(".")) dependsOn(
    uri("git://github.com/jrudolph/sbt-dependency-graph.git#v0.5.1") // or another tag/branch/revision
  )
}
```

Then, add the following in your `build.sbt`:

```scala
seq(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*)
```

In sbt you can then use the `dependency-graph` task in sbt to generate a graphml file
in `target/dependencies.graphml`. Use e.g. [yEd](http://www.yworks.com/en/products_yed_about.html)
to format the graph to your needs.

Standalone usage
----------------

You can use the project without sbt as well by either depending on the library and calling
`IvyGraphMLDependencies.transfrom(sourceIvyReport, targetFile)` or by just getting the binary
and calling it like `scala sbt-dependency-graph-0.5.1.jar <ivy-report-xml-path> <target-path>`.


Inner Workings
--------------

sbt/Ivy's `deliver-local` task create ivy-report xml-files inside `.ivy2/cache`. You can
just open them with your browser to look at the dependency report for your project.
This project takes the report xml of your project and creates a graphml file out of it. (BTW,
ivy can create graphml files itself, but since I didn't want to spend to much time getting
sbt to call into Ivy to create graphs, I went with the easy way here)

License
-------

Copyright (c) 2011 Johannes Rudolph

Published under the [Apache License 2.0](http://en.wikipedia.org/wiki/Apache_license).