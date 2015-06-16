# Coursier

*Pure Scala Artifact Fetching*

A pure Scala substitute for [Aether](http://www.eclipse.org/aether/)

Work in progress:
* full list of dependencies / version conflict resolution working, mildly to well tested,
* downloading/caching of JARs in its early stages.

Implements fancy Maven features like
* [POM inheritance](http://books.sonatype.com/mvnref-book/reference/pom-relationships-sect-project-relationships.html#pom-relationships-sect-project-inheritance),
* [dependency management](http://books.sonatype.com/mvnex-book/reference/optimizing-sect-dependencies.html),
* [import scope](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Importing_Dependencies),
* [properties](http://books.sonatype.com/mvnref-book/reference/resource-filtering-sect-properties.html).

Restricted to Maven resolution and repositories for now. Support for Ivy seems definitely at reach, just not done yet.

Both a JVM library and a Scala JS one.


Released under the Apache license, v2.
