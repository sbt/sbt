libraryDependencies ++= Seq(
	"com.novocode" % "junit-interface" % "0.5" % "test",
	"junit" % "junit" % "4.8" % "test"
)

libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-compiler" % _ )
