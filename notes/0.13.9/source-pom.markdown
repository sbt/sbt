
  [@cunei]: http://github.com/cunei
  [2001]: https://github.com/sbt/sbt/issues/2001
  [2027]: https://github.com/sbt/sbt/pull/2027

### Fixes with compatibility implications

- Starting with 0.13.9, the generated POM files no longer include dependencies on source or javadoc jars
  obtained via withSources() or withJavadoc()

### Improvements

### Bug fixes

### POM files no longer include certain source and javadoc jars

When declaring library dependencies using the withSources() or withJavadoc() options, sbt was also including
in the pom file, as dependencies, the source or javadoc jars using the default Maven scope. Such dependencies
might be erroneously processed as they were regular jars by automated tools

[#2001][2001]/[#2027][2027] by [@cunei][@cunei]
