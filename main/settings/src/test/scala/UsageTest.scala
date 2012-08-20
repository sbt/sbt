package sbt
package std

object UseTask
{
		import Def._

	val set = setting { 23 }
	val plain = PlainTaskMacro task { 19 }

	val x = task { set.value }
	val y = task { true }
	val z = task { if(y.value) x.value else plain.value }
	val a = taskDyn { 
		if(y.value) z else x
	}
}
object Assign
{
	import java.io.File
	import Def.{Initialize,macroValueT}
	import UseTask.{x,y,z,a,set,plain}

	val ak = TaskKey[Int]("a")
	val bk = TaskKey[Seq[Int]]("b")
	val ck = SettingKey[File]("c")
	val sk = TaskKey[Set[_]]("s")

/*	def azy = sk.value

	def azy2 = appmacro.Debug.checkWild(Def.task{ sk.value.size })

	val settings = Seq(
		ak += z.value + (if(y.value) set.value else plain.value),
		ck := new File(ck.value, "asdf"),
		ak := sk.value.size,
		bk ++= Seq(z.value)
	)*/

	def bool: Initialize[Boolean] = Def.setting { true }
	def enabledOnly[T](key: Initialize[T]): Initialize[Seq[T]] = Def.setting {
		val keys: Seq[T] = forallIn(key).value
		val enabled: Seq[Boolean] = forallIn(bool).value
		(keys zip enabled) collect { case (a, true) => a }
	}
	def forallIn[T](key: Initialize[T]): Initialize[Seq[T]] = Def.setting {
		key.value :: Nil
	}
}