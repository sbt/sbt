ThisBuild / scalaVersion := "3.8.4"
ThisBuild / organization := "com.example"

libraryDependencies += "com.typesafe" % "config" % "1.4.5"

val checkMetabuildClasspath = taskKey[Unit]("Checks the effective metabuild classpath")
val checkProjectClasspath = taskKey[Unit]("Checks the project classpath")

checkMetabuildClasspath := Def.uncached {
  val buildUnit = loadedBuild.value.units(thisProjectRef.value.build).unit
  val classpath = buildUnit.plugins.pluginData.dependencyClasspath
  val managedModules = classpath
    .flatMap(_.get(Keys.moduleIDStr))
    .map(Classpaths.moduleIdJsonKeyFormat.read)
    .map(module => module.organization -> module.name)
    .toSet
  val launcherOwnedModules = Set(
    "com.eed3si9n" -> "sjson-new-core_3",
    "com.eed3si9n" -> "gigahorse-core_3",
    "com.typesafe" -> "ssl-config-core_3",
    "org.reactivestreams" -> "reactive-streams",
    "org.slf4j" -> "slf4j-api",
    "com.typesafe" -> "config",
  )
  val pluginModules = Set(
    "ch.epfl.scala" -> "sbt-github-dependency-submission_sbt2_3",
    "com.eed3si9n" -> "gigahorse-asynchttpclient_3",
    "com.eed3si9n" -> "shaded-asynchttpclient",
  )
  val converter = buildUnit.converter
  val launcherJars = classpath
    .filter(_.get(Keys.moduleIDStr).isEmpty)
    .map(entry => converter.toPath(entry.data).getFileName.toString)
  val launcherJarNames = Set(
    "sjson-new-core_3",
    "gigahorse-core_3",
    "ssl-config-core_3",
    "reactive-streams",
    "slf4j-api",
    "config",
  )

  assert(
    managedModules.intersect(launcherOwnedModules).isEmpty,
    s"found plugin-managed sbt modules: ${managedModules.intersect(launcherOwnedModules)}",
  )
  assert(
    pluginModules.subsetOf(managedModules),
    s"missing plugin modules: ${pluginModules.diff(managedModules)}",
  )
  assert(
    launcherJarNames.forall(name => launcherJars.count(_.startsWith(s"$name-")) == 1),
    s"expected one launcher jar for each sbt module: $launcherJars",
  )
}

checkProjectClasspath := Def.uncached {
  val modules = (Compile / externalDependencyClasspath).value
    .flatMap(_.get(Keys.moduleIDStr))
    .map(Classpaths.moduleIdJsonKeyFormat.read)
  assert(
    modules.exists(module =>
      module.organization == "com.typesafe" &&
        module.name == "config" &&
        module.revision == "1.4.5"
    ),
    s"missing project dependency: $modules",
  )
}
