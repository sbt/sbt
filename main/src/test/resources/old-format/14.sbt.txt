name := "cms"

organization := "org.qirx"

scalaVersion := "2.11.1"

resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play" % "2.3.0",
  "com.typesafe.play" %% "play-test" % "2.3.0",
  "com.typesafe.play" %% "play-json" % "2.3.0",
  "org.qirx" %% "little-spec" % "0.4-SNAPSHOT" % "test",
  "org.qirx" %% "little-spec-extra-documentation" % "0.4-SNAPSHOT" % "test"
)

unmanagedSourceDirectories in Compile := Seq((scalaSource in Compile).value)

unmanagedSourceDirectories in Test := Seq((scalaSource in Test).value)

testFrameworks += new TestFramework("org.qirx.littlespec.sbt.TestFramework")

val x = taskKey[Unit /* Geef niets terug */]("test")

x := {
  val cacheDir = (streams in x).value.cacheDirectory / "unzipped"
  val dependencies = (externalDependencyClasspath in Compile).value
  val possibleJar = dependencies
    .map(a => a.data)
    .filter(f => f.getName contains "play-json")
    .headOption
  possibleJar match {
    case Some(file) => 
      val unzippedFiles = IO.unzip(from = file, toDirectory = cacheDir)
      // use flatmap because relativize returns an option, which can be flattened
      val names = unzippedFiles.flatMap(file => IO.relativize(base = cacheDir, file))
      println(s"Unzipped the following files in `$cacheDir`")
      names.foreach(println)
    case None => sys.error("Could not find jar")
  }
}
