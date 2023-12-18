lazy val check = taskKey[Unit]("Runs the check")

ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

def localCache =
  ivyPaths := IvyPaths(baseDirectory.value.toString, Some(((ThisBuild / baseDirectory).value / "ivy" / "cache").toString))

def commonSettings: Seq[Def.Setting[_]] =
  Seq(
    localCache,
    dependencyCacheDirectory := (LocalRootProject / baseDirectory).value / "dependency",
    scalaVersion := "2.10.4",
    resolvers += Resolver.sonatypeRepo("snapshots")
  )

def cachedResolutionSettings: Seq[Def.Setting[_]] =
  commonSettings ++ Seq(
    updateOptions := updateOptions.value.withCachedResolution(true)
  )

lazy val a = project.
  settings(cachedResolutionSettings: _*).
  settings(
    libraryDependencies := Seq(
      ("org.springframework"  % "spring-core"     % "3.2.2.RELEASE").force().exclude("org.springframework", "spring-asm"),
      ("org.springframework"  % "spring-tx"       % "3.1.2.RELEASE").force().exclude("org.springframework", "spring-asm"),
      ("org.springframework"  % "spring-beans"    % "3.2.2.RELEASE").force().exclude("org.springframework", "spring-asm"),
      ("org.springframework"  % "spring-context"  % "3.1.2.RELEASE").force().exclude("org.springframework", "spring-asm")
    )
  )

lazy val b = project.
  settings(commonSettings: _*).
  settings(
    libraryDependencies := Seq(
      ("org.springframework"  % "spring-core"     % "3.2.2.RELEASE").force().exclude("org.springframework", "spring-asm"),
      ("org.springframework"  % "spring-tx"       % "3.1.2.RELEASE").force().exclude("org.springframework", "spring-asm"),
      ("org.springframework"  % "spring-beans"    % "3.2.2.RELEASE").force().exclude("org.springframework", "spring-asm"),
      ("org.springframework"  % "spring-context"  % "3.1.2.RELEASE").force().exclude("org.springframework", "spring-asm")
    )
  )

lazy val c = project.
  dependsOn(a).
  settings(cachedResolutionSettings: _*).
  settings(
    libraryDependencies := Seq(
      // transitive force seems to be broken in ivy
      // "org.springframework" % "spring-core" % "4.0.3.RELEASE" exclude("org.springframework", "spring-asm")
    )
  )

lazy val d = project.
  dependsOn(b).
  settings(commonSettings: _*).
  settings(
    libraryDependencies := Seq(
      // transitive force seems to be broken in ivy
      // "org.springframework" % "spring-core" % "4.0.3.RELEASE" exclude("org.springframework", "spring-asm")
    )
  )

lazy val root = (project in file(".")).
  aggregate(a, b, c).
  settings(
    ThisBuild / organization := "org.example",
    ThisBuild / version := "1.0",
    check := {
      // sys.error(dependencyCacheDirectory.value.toString)
      val acp = (a / Compile / externalDependencyClasspath).value.sortBy {_.data.name}
      val bcp = (b / Compile / externalDependencyClasspath).value.sortBy {_.data.name}
      val ccp = (c / Compile / externalDependencyClasspath).value.sortBy {_.data.name}
      val dcp = (d / Compile / externalDependencyClasspath).value.sortBy {_.data.name}

      if (!(acp exists {_.data.name contains "spring-core-3.2.2.RELEASE"})) {
        sys.error("spring-core-3.2.2 is not found on a")
      }
      if (!(bcp exists {_.data.name contains "spring-core-3.2.2.RELEASE"})) {
        sys.error("spring-core-3.2.2 is not found on b")
      }
      if (!(ccp exists {_.data.name contains "spring-core-3.2.2.RELEASE"})) {
        sys.error("spring-core-3.2.2 is not found on c")
      }
      if (!(dcp exists {_.data.name contains "spring-core-3.2.2.RELEASE"})) {
        sys.error("spring-core-3.2.2 is not found on d\n" + dcp.toString)
      }
      if (!(acp exists {_.data.name contains "spring-tx-3.1.2.RELEASE"})) {
        sys.error("spring-tx-3.1.2 is not found on a")
      }
      if (!(bcp exists {_.data.name contains "spring-tx-3.1.2.RELEASE"})) {
        sys.error("spring-tx-3.1.2 is not found on b")
      }
      if (!(ccp exists {_.data.name contains "spring-tx-3.1.2.RELEASE"})) {
        sys.error("spring-tx-3.1.2 is not found on c")
      }
      if (!(dcp exists {_.data.name contains "spring-tx-3.1.2.RELEASE"})) {
        sys.error("spring-tx-3.1.2 is not found on d")
      }
      if (acp == bcp) ()
      else sys.error("Different classpaths are found:" +
        "\n - a (consolidated)  " + acp.toString +
        "\n - b (plain)         " + bcp.toString) 
      if (ccp == dcp) ()
      else sys.error("Different classpaths are found:" +
        "\n - c (consolidated)  " + ccp.toString +
        "\n - d (plain)         " + dcp.toString)
    }
  )
