lazy val check = taskKey[Unit]("Runs the check")

def commonSettings: Seq[Def.Setting[_]] =
  Seq(
    ivyPaths := IvyPaths( (baseDirectory in ThisBuild).value, Some((baseDirectory in LocalRootProject).value / "ivy-cache")),
    dependencyCacheDirectory := (baseDirectory in LocalRootProject).value / "dependency",
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
      "org.springframework"  % "spring-core"     % "3.2.2.RELEASE" force() exclude("org.springframework", "spring-asm"),
      "org.springframework"  % "spring-tx"       % "3.1.2.RELEASE" force() exclude("org.springframework", "spring-asm"),
      "org.springframework"  % "spring-beans"    % "3.2.2.RELEASE" force() exclude("org.springframework", "spring-asm"),
      "org.springframework"  % "spring-context"  % "3.1.2.RELEASE" force() exclude("org.springframework", "spring-asm")
    )
  )

lazy val b = project.
  settings(commonSettings: _*).
  settings(
    libraryDependencies := Seq(
      "org.springframework"  % "spring-core"     % "3.2.2.RELEASE" force() exclude("org.springframework", "spring-asm"),
      "org.springframework"  % "spring-tx"       % "3.1.2.RELEASE" force() exclude("org.springframework", "spring-asm"),
      "org.springframework"  % "spring-beans"    % "3.2.2.RELEASE" force() exclude("org.springframework", "spring-asm"),
      "org.springframework"  % "spring-context"  % "3.1.2.RELEASE" force() exclude("org.springframework", "spring-asm")
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
    organization in ThisBuild := "org.example",
    version in ThisBuild := "1.0",
    check := {
      // sys.error(dependencyCacheDirectory.value.toString)
      val acp = (externalDependencyClasspath in Compile in a).value.sortBy {_.data.getName}
      val bcp = (externalDependencyClasspath in Compile in b).value.sortBy {_.data.getName}
      val ccp = (externalDependencyClasspath in Compile in c).value.sortBy {_.data.getName}
      val dcp = (externalDependencyClasspath in Compile in d).value.sortBy {_.data.getName}
      
      if (!(acp exists {_.data.getName contains "spring-core-3.2.2.RELEASE"})) {
        sys.error("spring-core-3.2.2 is not found on a")
      }
      if (!(bcp exists {_.data.getName contains "spring-core-3.2.2.RELEASE"})) {
        sys.error("spring-core-3.2.2 is not found on b")
      }
      if (!(ccp exists {_.data.getName contains "spring-core-3.2.2.RELEASE"})) {
        sys.error("spring-core-3.2.2 is not found on c")
      }
      if (!(dcp exists {_.data.getName contains "spring-core-3.2.2.RELEASE"})) {
        sys.error("spring-core-3.2.2 is not found on d\n" + dcp.toString)
      }
      if (!(acp exists {_.data.getName contains "spring-tx-3.1.2.RELEASE"})) {
        sys.error("spring-tx-3.1.2 is not found on a")
      }
      if (!(bcp exists {_.data.getName contains "spring-tx-3.1.2.RELEASE"})) {
        sys.error("spring-tx-3.1.2 is not found on b")
      }
      if (!(ccp exists {_.data.getName contains "spring-tx-3.1.2.RELEASE"})) {
        sys.error("spring-tx-3.1.2 is not found on c")
      }
      if (!(dcp exists {_.data.getName contains "spring-tx-3.1.2.RELEASE"})) {
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
