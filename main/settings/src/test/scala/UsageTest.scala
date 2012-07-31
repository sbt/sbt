package sbt
package std

object UseTask
{
		import Def._
		import TaskMacro.{task, taskDyn}

	val set = SettingMacro setting { 23 }
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
	import Def.macroValueT
	import UseTask.{x,y,z,a,set,plain}

	val ak = TaskKey[Int]("a")
	val bk = TaskKey[Seq[Int]]("b")
	val ck = SettingKey[File]("c")
	val sk = TaskKey[Set[_]]("s")

	def azy = sk.value

	val settings = Seq(
		ak += z.value + (if(y.value) set.value else plain.value),
		bk ++= Seq(z.value),
		ck := new File(ck.value, "asdf"),
		ak := sk.value.size + sk.value.size
	)
}