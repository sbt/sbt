{
		import complete.DefaultParsers._
	val parser = token(Space ~> ( ("exists" ^^^ true) | ("absent" ^^^ false) ) )
	InputKey[Unit]("checkOutput") := {
		val shouldExist = parser.parsed
		val dir = (classDirectory in Compile).value
		if((dir / "Anon.class").exists != shouldExist)
			sys.error("Top level class incorrect" )
		else if( (dir / "Anon$1.class").exists != shouldExist)
			sys.error("Inner class incorrect" )
		else
			()
	}
}
