package sbt

import Def.ScopedKey

final case class ScopedKeyData[A](scoped: ScopedKey[A], value: Any) {
  import sbt.internal.util.Types.const
  val key = scoped.key
  val scope = scoped.scope
  def typeName: String = fold(fmtMf("Task[%s]"), fmtMf("InputTask[%s]"), key.manifest.toString)
  def settingValue: Option[Any] = fold(const(None), const(None), Some(value))
  def description: String =
    fold(fmtMf("Task: %s"),
         fmtMf("Input task: %s"),
         "Setting: %s = %s" format (key.manifest.toString, value.toString))
  def fold[T](targ: OptManifest[_] => T, itarg: OptManifest[_] => T, s: => T): T =
    key.manifest.runtimeClass match {
      case TaskClass      => targ(key.manifest.typeArguments.head)
      case InputTaskClass => itarg(key.manifest.typeArguments.head)
      case _              => s
    }
  def fmtMf(s: String): OptManifest[_] => String = s format _

  private val TaskClass = classOf[Task[_]]
  private val InputTaskClass = classOf[InputTask[_]]
}
