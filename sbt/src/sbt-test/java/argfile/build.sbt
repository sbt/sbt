ScalaSource in Configurations.Compile <<= Source( _ / " scala test " )

JavaSource in Configurations.Compile <<= Source( _ / " java test " )

TaskKey("init") <<= (ScalaSource in Configurations.Compile, JavaSource in Configurations.Compile) map { (ss, js) =>
	import IO._
	createDirectories(ss :: js :: Nil)
	copyFile(file("changes") / "Test.scala", ss / " Test s.scala")
	copyFile(file("changes") / "A.java", js / "a" / "A.java")
	delete(file("changes"))
}
	