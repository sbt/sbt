TaskKey[Unit]("check") := {
	import antlr.Tool // verify that antlr is on compile classpath
	Class.forName("antlr.Tool") // verify antlr is on runtime classpath
	()
}