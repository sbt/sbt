sbt-dependency-graph
====================

Create a graph (in graphml format) from your project's dependencies.

Requirements
------------

* Simple Build Tool

How To Use
----------

For sbt 0.11, in `project/plugins/project/build.scala`:

```scala
import sbt._

object Plugins extends Build {
  lazy val root = Project("root", file(".")) dependsOn(
    uri("git://github.com/jrudolph/sbt-dependency-graph.git#XX") // where XX is tag/branch
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


Inner Workings
--------------

The task relies on sbt/Ivy's `deliver-local` task to create an ivy report inside the `.ivy2/cache`
directory. This report is then transformed into a graphml file.

License
-------

Copyright (c) 2011 Johannes Rudolph

Published under the [Apache License 2.0](http://en.wikipedia.org/wiki/Apache_license).