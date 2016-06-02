  [milessabin]: https://github.com/milessabin
  [2286]: https://github.com/sbt/sbt/issues/2286
  [2634]: https://github.com/sbt/sbt/pull/2634

### Fixes with compatibility implications

- By default the Scala toolchain artefacts are now transitively resolved using the provided `scalaVersion` and
  `scalaOrganization`. Previously a user specified `scalaOrganization` would not have affected transitive
  dependencies on, eg. `scala-reflect`. An Ivy-level mechanism is used for this purpose, and as a consequence
  the overriding happens early in the resolution process which might improve resolution times, and as a side
  benefit fixes [#2286][2286]. The old behaviour can be restored by adding
  `ivyScala := { ivyScala.value map {_.copy(overrideScalaVersion = sbtPlugin.value)} }`
  to your build. [#2286][2286]/[#2634][2634] by [@milessabin][milessabin]

### Improvements

### Bug fixes
