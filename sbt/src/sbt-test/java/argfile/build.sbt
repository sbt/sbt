scalaSource in Configurations.Compile := sourceDirectory( _ / " scala test " ).value

javaSource in Configurations.Compile := sourceDirectory( _ / " java test " ).value

TaskKey[Unit]("init") := ((scalaSource in Configurations.Compile, javaSource in Configurations.Compile) map { (ss, js) =>
	import IO._
	createDirectories(ss :: js :: Nil)
	copyFile(file("changes") / "Test.scala", ss / " Test s.scala")
	copyFile(file("changes") / "A.java", js / "a" / "A.java")
	delete(file("changes"))
}).value
