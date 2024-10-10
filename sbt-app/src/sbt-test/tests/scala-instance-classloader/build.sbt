import sbt.internal.inc.ScalaInstance

lazy val OtherScala = config("other-scala").hide
lazy val junitinterface = "com.novocode" % "junit-interface" % "0.11"
lazy val akkaActor = "com.typesafe.akka" %% "akka-actor" % "2.5.17"
ThisBuild / scalaVersion := "2.12.20"

lazy val root = (project in file("."))
  .configs(OtherScala)
  .settings(
    libraryDependencies += {
      "org.scala-lang" % "scala-compiler" % scalaVersion.value % OtherScala.name 
    },
    managedClasspath in OtherScala := Classpaths.managedJars(OtherScala, classpathTypes.value, update.value),

    // Hack in the scala instance
    scalaInstance := {
      val rawJars = (managedClasspath in OtherScala).value.map(_.data)
      val scalaHome = (target.value / "scala-home")
      def removeVersion(name: String): String =
        name.replaceAll("\\-2.12.11", "")
      for(jar <- rawJars) {
        val tjar = scalaHome / s"lib/${removeVersion(jar.getName)}"
        IO.copyFile(jar, tjar)
      }
      IO.listFiles(scalaHome).foreach(f => System.err.println(s" * $f}"))
      ScalaInstance(scalaHome, appConfiguration.value.provider.scalaProvider.launcher)
    },

    libraryDependencies += junitinterface % Test,
    libraryDependencies += akkaActor % Test,

    scalaModuleInfo := {
      val old = scalaModuleInfo.value
      old map { _.withOverrideScalaVersion(sbtPlugin.value) }
    }
  )
