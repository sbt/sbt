package sbt

	import Def.ScopedKey

final case class ScopedKeyData[A](scoped: ScopedKey[A], value: Any)
{
	import Types.const
	val key = scoped.key
	val scope = scoped.scope
	def typeName: String = fold(fmtMf("Task[%s]"), fmtMf("InputTask[%s]"), key.manifest.toString)
	def settingValue: Option[Any] = fold(const(None), const(None), Some(value))
	def description: String = fold(fmtMf("Task: %s"), fmtMf("Input task: %s"),
		"Setting: %s = %s" format (key.manifest.toString, value.toString))
	def fold[A](targ: OptManifest[_] => A, itarg: OptManifest[_] => A, s: => A): A =
		if (key.manifest.erasure == classOf[Task[_]]) targ(key.manifest.typeArguments.head)
		else if (key.manifest.erasure == classOf[InputTask[_]]) itarg(key.manifest.typeArguments.head)
		else s
	def fmtMf(s: String): OptManifest[_] => String = s format _
}
