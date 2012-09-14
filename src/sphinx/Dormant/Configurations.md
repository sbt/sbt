[Ivy documentation]: http://ant.apache.org/ivy/history/2.2.0/tutorial/conf.html
[Maven Scopes]: http://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Dependency_Scope

_Wiki Maintenance Note:_ Most of what's on this page is now covered in
[[Getting Started Library Dependencies]]. This page should be
analyzed for any points that aren't covered on the new page, and
those points moved somewhere (maybe the [[FAQ]] or an "advanced
library deps" page). Then this page could become a redirect with
no content except a link pointing to the new page(s).

_Wiki Maintenance Note 2:_ There probably should be a page called
Configurations that's less about library dependency management and
more about listing all the configurations that exist and
describing what they are used for. This would complement the way
this page is linked, for example in [[Index]].

# Configurations

Ivy configurations are a useful feature for your build when you use managed dependencies.  They are essentially named sets of dependencies.  You can read the [Ivy documentation] for details.  Their use in sbt is described on this page.

# Usage

The built-in use of configurations in sbt is similar to scopes in Maven.  sbt adds dependencies to different classpaths by the configuration that they are defined in.  See the description of [Maven Scopes] for details.

You put a dependency in a configuration by selecting one or more of its configurations to map to one or more of your project's configurations.  The most common case is to have one of your configurations `A` use a dependency's configuration `B`.  The mapping for this looks like `"A->B"`.  To apply this mapping to a dependency, add it to the end of your dependency definition:

```scala
libraryDependencies += "org.scalatest" % "scalatest" % "1.2" % "test->compile"
```

This says that your project's `test` configuration uses `ScalaTest`'s `default` configuration.  Again, see the [Ivy documentation] for more advanced mappings.  Most projects published to Maven repositories will use the `default` or `compile` configuration.

A useful application of configurations is to group dependencies that are not used on normal classpaths.  For example, your project might use a `"js"` configuration to automatically download jQuery and then include it in your jar by modifying `resources`.  For example:

```scala
ivyConfigurations += config("js") hide

libraryDependencies += "jquery" % "jquery" % "1.3.2" % "js->default" from "http://jqueryjs.googlecode.com/files/jquery-1.3.2.min.js"

resources <<= (resources, update) { (rs, report) =>
	rs ++ report.select( configurationFilter("js") )
}
```

The `config` method defines a new configuration with name `"js"` and makes it private to the project so that it is not used for publishing.
See [[Update Report]] for more information on selecting managed artifacts.

A configuration without a mapping (no `"->"`) is mapped to `default` or `compile`.  The `->` is only needed when mapping to a different configuration than those.  The ScalaTest dependency above can then be shortened to:

```scala
libraryDependencies += "org.scala-tools.testing" % "scalatest" % "1.0" % "test"
```
