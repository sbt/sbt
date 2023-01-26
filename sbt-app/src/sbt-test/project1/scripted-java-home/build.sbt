lazy val scriptedJavaVersion = settingKey[Long]("")

lazy val root = (project in file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    scriptedJavaVersion := {
      val versions = discoveredJavaHomes.value
        .map { case (jv, _) => JavaVersion(jv).numbers }
        .collect {
          case Vector(1L, ver, _*) => ver
          case Vector(ver, _*)     => ver
        }
      if (versions.isEmpty) sys.error("No Java versions discovered")
      else versions.max
    },
    commands += Command.command("setJavaVersion") { state =>
      val extracted = Project.extract(state)
      import extracted._
      val jv = (currentRef / scriptedJavaVersion).get(structure.data).get
      s"java++ $jv!" :: state
    },
    scriptedLaunchOpts += s"-Dscripted.java.version=${scriptedJavaVersion.value}"
  )
