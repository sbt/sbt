import sbt.complete.DefaultParsers._

InputKey("check-output") <<= {
	val parser = token(Space ~> ( ("exists" ^^^ true) | ("absent" ^^^ false) ) )
	def action(result: TaskKey[Boolean]) =
		(ClassDirectory in Configurations.Compile, result) map { (dir, shouldExist) =>
			if((dir / "Anon.class").exists != shouldExist) error("Top level class incorrect" )
			else if( (dir / "Anon$1.class").exists != shouldExist) error("Inner class incorrect" )
		}
	InputTask(s => parser)(action)
}