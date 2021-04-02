lazy val check = taskKey[Unit]("tests update")

def commonSettings: Seq[Def.Setting[_]] = Seq(
    resolvers ++= Vector(Resolver.typesafeIvyRepo("releases"), Resolver.typesafeRepo("releases"), Resolver.sbtPluginRepo("releases")),
    check := {
      val ur = update.value
      import sbinary._, Operations._, DefaultProtocol._
      import Cache.seqFormat, CacheIvy._
      toByteArray(ur)
    }
  )

lazy val projA = project.
  settings(commonSettings: _*).
  settings(
    addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.3.2"),
    addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager"  % "0.7.3")
  )
