/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.std

import sbt.internal.util.complete
import sbt.internal.util.complete.DefaultParsers
import sbt.{ Def, InputTask, Task }

/*object UseTask
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
}*/
object Assign {
  import java.io.File

  import Def.{ Initialize, inputKey, macroValueT, parserToInput, settingKey, taskKey }
  //	import UseTask.{x,y,z,a,set,plain}

  val ak = taskKey[Int]("a")
  val bk = taskKey[Seq[Int]]("b")
  val ck = settingKey[File]("c")
  val sk = taskKey[Set[_]]("s")

  val ik = inputKey[Int]("i")
  val isk = inputKey[String]("is")
  val mk = settingKey[Int]("m")
  val tk = taskKey[Int]("t")
  val name = settingKey[String]("name")
  val dummyt = taskKey[complete.Parser[String]]("dummyt")
  val dummys = settingKey[complete.Parser[String]]("dummys")
  val dummy3 = settingKey[complete.Parser[(String, Int)]]("dummy3")
  val tsk: complete.Parser[Task[String]] = DefaultParsers.failure("ignored")
  val itsk: Initialize[InputTask[Int]] = inputKey[Int]("ignored")
  val seqSetting = settingKey[Seq[String]]("seqSetting")
  val listSetting = settingKey[List[String]]("listSetting")

  /*	def azy = sk.value

	def azy2 = appmacro.Debug.checkWild(Def.task{ sk.value.size })

	val settings = Seq(
		ak += z.value + (if(y.value) set.value else plain.value),
		ck := new File(ck.value, "asdf"),
		ak := sk.value.size,
		bk ++= Seq(z.value)
	)*/

  val zz = Def.task {
    mk.value + tk.value + mk.value + tk.value + mk.value + tk.value + mk.value + tk.value + mk.value + tk.value + mk.value + tk.value
  }

  import DefaultParsers._
  val p = Def.setting { name.value ~> Space ~> ID }
  val is = Seq(
    mk := 3,
    name := "asdf",
    tk := (math.random * 1000).toInt,
    isk := dummys.value.parsed // should not compile: cannot use a task to define the parser
    //		ik := { if( tsk.parsed.value == "blue") tk.value else mk.value }
  )

  val it1 = Def.inputTask {
    tsk.parsed //"as" //dummy.value.parsed
  }
  val it2 = Def.inputTask {
    "lit"
  }

  val it3: Initialize[InputTask[String]] = Def.inputTask[String] {
    tsk.parsed.value + itsk.parsed.value.toString + isk.evaluated
  }
  // should not compile: cannot use a task to define the parser
  /*	val it4 = Def.inputTask {
		dummyt.value.parsed
	}*/
  // should compile: can use a setting to define the parser
  val it5 = Def.inputTask {
    dummys.parsed
  }
  val it6 = Def.inputTaskDyn {
    val d3 = dummy3.parsed
    val i = d3._2
    Def.task { tk.value + i }
  }

  val it7 = Def.inputTask {
    it5.parsed
  }

  def bool: Initialize[Boolean] = Def.setting { true }
  def enabledOnly[T](key: Initialize[T]): Initialize[Seq[T]] = Def.setting {
    val keys: Seq[T] = forallIn(key).value
    val enabled: Seq[Boolean] = forallIn(bool).value
    (keys zip enabled) collect { case (a, true) => a }
  }
  def forallIn[T](key: Initialize[T]): Initialize[Seq[T]] = Def.setting {
    key.value :: Nil
  }

  // Test that Append.Sequence instances for Seq/List work and don't mess up with each other
  seqSetting := Seq("test1")
  seqSetting ++= Seq("test2")
  seqSetting ++= List("test3")
  seqSetting += "test4"

  listSetting := List("test1")
  listSetting ++= List("test2")
  listSetting += "test4"
}
