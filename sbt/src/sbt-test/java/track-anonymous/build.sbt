{
		import sbt.complete.DefaultParsers._
	val parser = token(Space ~> ( ("exists" ^^^ true) | ("absent" ^^^ false) ) )
	InputKey[Unit]("check-output") := {
		val shouldExist = parser.parsed
		val dir = (classDirectory in Compile).value
		if((dir / "Anon.class").exists != shouldExist)
			error("Top level class incorrect" )
		else if( (dir / "Anon$1.class").exists != shouldExist)
			error("Inner class incorrect" )
		else
			()
	}
}