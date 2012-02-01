package sbt

	import java.io.File

object BasicKeys
{
	val historyPath = AttributeKey[Option[File]]("history", "The location where command line history is persisted.")
	val shellPrompt = AttributeKey[State => String]("shell-prompt", "The function that constructs the command prompt from the current build state.")
	val watch = AttributeKey[Watched]("watch", "Continuous execution configuration.")
}
