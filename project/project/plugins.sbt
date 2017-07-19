addSbtPlugin("io.get-coursier" % "sbt-coursier" % coursierVersion)
addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.3.0")

// important: this line is matched / substituted during releases (via sbt-release)
def coursierVersion = "1.0.0-RC8"

// required for just released things
resolvers += Resolver.sonatypeRepo("releases")
