/*
 * sbt
 * Copyright 2026, Scala center
 * Licensed under Apache License 2.0 (see LICENSE)
 */

addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.3.2")
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.11.7")

Compile / unmanagedSources ++= {
  val root = file(sys.props("sbt.build.root"))
  Seq("PublishBinPlugin.scala", "PackageSignerPlugin.scala").map(root / "project" / _)
}
