  [@jsuereth]: https://github.com/jsuereth
  [1676]: https://github.com/sbt/sbt/issues/1676
  [1322]: https://github.com/sbt/sbt/issues/1322
  [679]: https://github.com/sbt/sbt/issues/679
  [647]: https://github.com/sbt/sbt/issues/647
  [1616]: https://github.com/sbt/sbt/issues/1616

### Fixes with compatibility implications

### Improvements

### Aether Resolution

sbt 0.13.8 adds the ability to use Eclipse Aether to resolve maven dependencies.  This is designed to work within Ivy
so that both Aether + Ivy dependencies cohesively depend on each other.

The key called `updateOptions` has been expanded to enable Aether resolutions via the following setting:

    updateOptions := updateOptions.value.withAetherResolution(true)

This will create a new `~/.ivy2/maven-cache` directory which contains the Aether cache of files.   You may notice some
file will be re-downloaded for the new cache layout.   Additionally, sbt will now be able to fully construct
`maven-metadata.xml` files when publishing to remote repositories or when publishing to the local `~/.m2/repository`.
This should help erase many of the deficiencies encountered when using Maven and sbt together.

Note:  The setting must be places on EVERY subproject within a build if you wish to fully use Aether for all projects.

Known limitations:

* The current implementation does not support ivy-style version numbers, such as "2.10.+" or "latest.snapshot".  This
  is a fixable situation, but the version range query and Ivy -> Maven version range translation code has not been migrated.



### Bug fixes

- sbt doens't honor Maven's uniqueVersions (use aether resolver to fix). [#1322][1322]  by [@jsuereth][@jsuereth]
- sbt doens't see new SNAPSHOT dependency versions in local maven repos (use withLatestSnapshots + aether resolver to fix) [#321][321] by [@jsuereth][@jsuereth]
- Property in pom's version field results to wrong dependency resolution (use aether resolver to fix). [#647][647] by [@jsuereth][@jsuereth]
- Maven local resolver with parent POM (use aether resolver). [#1616][1616] by [@jsuereth][@jsuereth]

// Possibly fixed, need verification.
- 1676 - SNAPSHOT dependency not updated ???
- 679 - Incorrect Maven Snapshot file resolution ????