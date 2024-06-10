import sbt.internal.inc.ScalaInstance

lazy val OtherScala = config("other-scala").hide
lazy val junitinterface = "com.novocode" % "junit-interface" % "0.11"
lazy val akkaActor = "com.typesafe.akka" %% "akka-actor" % "2.5.17"
ThisBuild / scalaVersion := "2.12.19"

lazy val root = (project in file("."))
  .configs(OtherScala)
  .settings(
    libraryDependencies += {
      "org.scala-lang" % "scala-compiler" % scalaVersion.value % OtherScala.name 
    },
    OtherScala / managedClasspath := 
      Classpaths.managedJars(OtherScala, classpathTypes.value, update.value, fileConverter.value),

    // Hack in the scala instance
    scalaInstance := {
      val converter = fileConverter.value
      val rawJars = (OtherScala / managedClasspath).value.map(c => converter.toPath(c.data).toFile)
      val scalaHome = (target.value / "scala-home")
      val sv = scalaVersion.value
      def removeVersion(name: String): String = name.replaceAll(s"\\-$sv", "")
      for(jar <- rawJars) {
        IO.copyFile(jar, scalaHome / s"lib" / removeVersion(jar.getName))
      }
      IO.listFiles(scalaHome / "lib").foreach(f => System.err.println(s" * $f"))
      ScalaInstance(scalaHome, appConfiguration.value.provider.scalaProvider.launcher)
    },

    libraryDependencies += junitinterface % Test,
    libraryDependencies += akkaActor % Test,

    scalaModuleInfo := {
      val old = scalaModuleInfo.value
      old map { _.withOverrideScalaVersion(sbtPlugin.value) }
    }
  )
