// The library serves two purposes:
// 1. add some non-standard library to the meta-build classpath to later check that it's included into updateSbtClassifiers
// 2. use assertions from junit in custom assertion in `build.sbt` of current scripted test
libraryDependencies += "junit" % "junit" % "4.13.2"