import sbt.complete.DefaultParsers._

InputKey[Unit]("check-output") <<= {
	val parser = token(Space ~> ( ("exists" ^^^ true) | ("absent" ^^^ false) ) )
	def action(result: TaskKey[Boolean]) =
		(classDirectory in Configurations.Compile, result) map { (dir, shouldExist) =>
			if((dir / "Anon.class").exists != shouldExist)
				error("Top level class incorrect" )
			else if( (dir / "Anon$1.class").exists != shouldExist)
				error("Inner class incorrect" )
			else
				()
		}
	InputTask(s => parser)(action)
}