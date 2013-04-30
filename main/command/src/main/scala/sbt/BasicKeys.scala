package sbt

	import java.io.File

object BasicKeys
{
	val historyPath = AttributeKey[Option[File]]("history", "The location where command line history is persisted.", 40)
	val shellPrompt = AttributeKey[State => String]("shell-prompt", "The function that constructs the command prompt from the current build state.", 10000)
	val watch = AttributeKey[Watched]("watch", "Continuous execution configuration.", 1000)
	private[sbt] val interactive = AttributeKey[Boolean]("interactive", "True if commands are currently being entered from an interactive environment.", 10)
	private[sbt] val classLoaderCache = AttributeKey[classpath.ClassLoaderCache]("class-loader-cache", "Caches class loaders based on the classpath entries and last modified times.", 10)
	private[sbt] val OnFailureStack = AttributeKey[List[Option[String]]]("on-failure-stack", "Stack that remembers on-failure handlers.", 10)
}
