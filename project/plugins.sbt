addSbtPlugin("org.xerial.sbt" % "sbt-pack" % "0.8.2")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.14")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.0.0")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.4.0")
addSbtPlugin("org.tpolecat" % "tut-plugin" % "0.4.8")
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-M15-2")
addSbtPlugin("io.get-coursier" % "sbt-shading" % "1.0.0-M15-2")
addSbtPlugin("com.typesafe.sbt" % "sbt-proguard" % "0.2.2")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.1.13")
libraryDependencies += "org.scala-sbt" % "scripted-plugin" % sbtVersion.value

// temporary, until sbt-shading 1.0.0-M15-3 (that will pull a fine jarjar version)
resolvers += Resolver.mavenLocal
libraryDependencies += {

  def jarjarVersion = {
    val coursierJarjarVersion = "1.0.1-coursier-SNAPSHOT"
    val fallbackJarjarVersion = "1.0.0"
    def coursierJarjarFoundInM2 = (file(sys.props("user.home")) / s".m2/repository/org/anarres/jarjar/jarjar-core/$coursierJarjarVersion").exists()

    if (sys.env.contains("CI") || coursierJarjarFoundInM2)
      coursierJarjarVersion
    else {
      scala.Console.err.println(
        "Ad hoc jarjar version not found. Run\n" +
        "  git clone https://github.com/alexarchambault/jarjar.git && cd jarjar && git checkout 249c8dbb970f8 && ./gradlew install\n" +
        s"to install it. Using version $fallbackJarjarVersion, that doesn't properly\n" +
        "shade Scala JARs."
      )
      fallbackJarjarVersion
    }
  }

  "org.anarres.jarjar" % "jarjar-core" % jarjarVersion
}
