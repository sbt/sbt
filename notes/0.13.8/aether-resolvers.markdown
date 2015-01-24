  [@jsuereth]: https://github.com/jsuereth
  [1676]: https://github.com/sbt/sbt/issues/1676
  [1322]: https://github.com/sbt/sbt/issues/1322
  [679]: https://github.com/sbt/sbt/issues/679
  [647]: https://github.com/sbt/sbt/issues/647
  [1616]: https://github.com/sbt/sbt/issues/1616

### Fixes with compatibility implications

### Improvements

### Maven resolver plugin

sbt 0.13.8 adds an extension point in the dependency resolution to customize Maven resolvers.
This allows us to write sbt-maven-resolver auto plugin, which internally uses Eclipse Aether
to resolve Maven dependencies instead of Apache Ivy.

To enable this plugin, add the following to `project/maven.sbt` (or `project/plugin.sbt` the file name doesn't matter):

    libraryDependencies += Defaults.sbtPluginExtra("org.scala-sbt" % "sbt-maven-resolver" % sbtVersion.value,
      sbtBinaryVersion.value, scalaBinaryVersion.value)

This will create a new `~/.ivy2/maven-cache` directory, which contains the Aether cache of files.
You may notice some file will be re-downloaded for the new cache layout.
Additionally, sbt will now be able to fully construct
`maven-metadata.xml` files when publishing to remote repositories or when publishing to the local `~/.m2/repository`.
This should help erase many of the deficiencies encountered when using Maven and sbt together.

**Notes and known limitations**:

- sbt-maven-resolver requires sbt 0.13.8 and above.
- The current implementation does not support Ivy-style dynamic revisions, such as "2.10.+" or "latest.snapshot".  This
  is a fixable situation, but the version range query and Ivy -> Maven version range translation code has not been migrated.

### Bug fixes

- sbt doesn't honor Maven's uniqueVersions (use sbt-maven-resolver to fix). [#1322][1322]  by [@jsuereth][@jsuereth]
- sbt doesn't see new SNAPSHOT dependency versions in local maven repos (use withLatestSnapshots + sbt-maven-resolver to fix) [#321][321] by [@jsuereth][@jsuereth]
- Property in pom's version field results to wrong dependency resolution (use sbt-maven-resolver to fix). [#647][647] by [@jsuereth][@jsuereth]
- Maven local resolver with parent POM (use sbt-maven-resolver). [#1616][1616] by [@jsuereth][@jsuereth]

// Possibly fixed, need verification.
- 1676 - SNAPSHOT dependency not updated ???
- 679 - Incorrect Maven Snapshot file resolution ????
