// https://github.com/sbt/sbt/issues/8410
scalaVersion := "3.3.4"
libraryDependencies ++= Seq(
  "org.typelevel" %% "weaver-cats" % "0.8.4" % Test,
  "com.siriusxm" %% "snapshot4s-weaver" % "0.1.5" % Test,
)
