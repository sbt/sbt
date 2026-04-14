// https://github.com/sbt/sbt/issues/8410
scalaVersion := "3.3.7"
libraryDependencies ++= Seq(
  "org.typelevel" %% "weaver-cats" % "0.11.1" % Test,
  "com.siriusxm" %% "snapshot4s-weaver" % "0.2.2" % Test,
)
