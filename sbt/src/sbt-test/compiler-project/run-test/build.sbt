scalaVersion in ThisBuild := "2.12.3"

libraryDependencies ++= Seq(
	"com.novocode" % "junit-interface" % "0.5" % Test,
	"junit" % "junit" % "4.8" % Test,
  "commons-io" % "commons-io" % "2.5" % Runtime,
)

libraryDependencies += scalaVersion("org.scala-lang" % "scala-compiler" % _ ).value
