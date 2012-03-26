package sbt

	import java.io.File

object BasicKeys
{
	val historyPath = AttributeKey[Option[File]]("history", "The location where command line history is persisted.", 40)
	val shellPrompt = AttributeKey[State => String]("shell-prompt", "The function that constructs the command prompt from the current build state.", 10000)
	val watch = AttributeKey[Watched]("watch", "Continuous execution configuration.", 1000)
}
