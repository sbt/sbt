package sbt

	import java.io.File

object Def extends Init[Scope]
{
	type Classpath = Seq[Attributed[File]]

	val triggeredBy = AttributeKey[Seq[Task[_]]]("triggered-by")
	val runBefore = AttributeKey[Seq[Task[_]]]("run-before")
	private[sbt] val parseResult: TaskKey[Any] = TaskKey("$parse-result", "Internal: used to implement input tasks.", KeyRanks.Invisible)
	// TODO: move back to Keys
	val resolvedScoped = SettingKey[ScopedKey[_]]("resolved-scoped", "The ScopedKey for the referencing setting or task.", KeyRanks.DSetting)

	lazy val showFullKey: Show[ScopedKey[_]] = showFullKey(None)
	def showFullKey(keyNameColor: Option[String]): Show[ScopedKey[_]] =
		new Show[ScopedKey[_]] { def apply(key: ScopedKey[_]) = displayFull(key, keyNameColor) }

	def showRelativeKey(current: ProjectRef, multi: Boolean, keyNameColor: Option[String] = None): Show[ScopedKey[_]] = new Show[ScopedKey[_]] {
		def apply(key: ScopedKey[_]) =
			Scope.display(key.scope, colored(key.key.label, keyNameColor), ref => displayRelative(current, multi, ref))
	}
	def displayRelative(current: ProjectRef, multi: Boolean, project: Reference): String = project match {
		case BuildRef(current.build) => "{.}/"
		case `current` => if(multi) current.project + "/" else ""
		case ProjectRef(current.build, x) => x + "/"
		case _ => Reference.display(project) + "/"
	}
	def displayFull(scoped: ScopedKey[_]): String = displayFull(scoped, None)
	def displayFull(scoped: ScopedKey[_], keyNameColor: Option[String]): String = Scope.display(scoped.scope, colored(scoped.key.label, keyNameColor))
	def displayMasked(scoped: ScopedKey[_], mask: ScopeMask): String = Scope.displayMasked(scoped.scope, scoped.key.label, mask)

	def colored(s: String, color: Option[String]): String = color match {
		case Some(c) => c + s + scala.Console.RESET
		case None => s
	}
}