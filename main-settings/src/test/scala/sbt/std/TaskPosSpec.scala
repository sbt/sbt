/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.std

class TaskPosSpec {
  // Dynamic tasks can have task invocations inside if branches
  locally {
    import sbt._
    import sbt.Def._
    val foo = taskKey[String]("")
    val bar = taskKey[String]("")
    var condition = true
    val baz = Def.taskDyn[String] {
      if (condition) foo
      else bar
    }
  }

  // Dynamic settings can have setting invocations inside if branches
  locally {
    import sbt._
    import sbt.Def._
    val foo = settingKey[String]("")
    val bar = settingKey[String]("")
    var condition = true
    val baz = Def.settingDyn[String] {
      if (condition) foo
      else bar
    }
  }

  locally {
    import sbt._
    import sbt.Def._
    val foo = taskKey[String]("")
    var condition = true
    val baz = Def.task[String] {
      val fooAnon = () => foo.value: @sbtUnchecked
      if (condition) fooAnon()
      else fooAnon()
    }
  }

  locally {
    import sbt._
    import sbt.Def._
    val foo = taskKey[String]("")
    var condition = true
    val baz = Def.task[String] {
      val fooAnon = () => (foo.value: @sbtUnchecked) + ""
      if (condition) fooAnon()
      else fooAnon()
    }
  }

  locally {
    import sbt._
    import sbt.Def._
    val foo = taskKey[String]("")
    val bar = taskKey[String]("")
    var condition = true
    val baz = Def.task[String] {
      if (condition) foo.value: @sbtUnchecked
      else bar.value: @sbtUnchecked
    }
  }

  locally {
    // This is fix 1 for appearance of tasks inside anons
    import sbt._
    import sbt.Def._
    val foo = taskKey[String]("")
    var condition = true
    val baz = Def.task[String] {
      val fooResult = foo.value
      val anon = () => fooResult + " "
      if (condition) anon()
      else ""
    }
  }

  locally {
    // This is fix 2 for appearance of tasks inside anons
    import sbt._
    import sbt.Def._
    val foo = taskKey[String]("")
    var condition = true
    val baz = Def.taskDyn[String] {
      val anon1 = (value: String) => value + " "
      if (condition) {
        Def.task(anon1(foo.value))
      } else Def.task("")
    }
  }

  locally {
    // missing .value error should not happen inside task dyn
    import sbt._
    import sbt.Def._
    val foo = taskKey[String]("")
    val baz = Def.taskDyn[String] {
      foo
    }
  }

  locally {
    // missing .value error should not happen inside task dyn
    import sbt._
    import sbt.Def._
    val foo = taskKey[String]("")
    val avoidDCE = ""
    val baz = Def.task[String] {
      foo: @sbtUnchecked
      avoidDCE
    }
  }

  locally {
    import sbt._
    import sbt.Def._
    val foo = taskKey[String]("")
    val baz = Def.task[String] {
      def inner(s: KeyedInitialize[_]) = println(s)
      inner(foo)
      ""
    }
  }

  locally {
    // In theory, this should be reported, but missing .value analysis is dumb at the cost of speed
    import sbt._
    import sbt.Def._
    val foo = taskKey[String]("")
    def avoidDCE = { println(""); "" }
    val baz = Def.task[String] {
      val (_, _) = "" match {
        case _ => (foo, 1 + 2)
      }
      avoidDCE
    }
  }

  locally {
    import sbt._
    import sbt.Def._
    val foo = taskKey[String]("")
    def avoidDCE = { println(""); "" }
    val baz = Def.task[String] {
      val hehe = foo
      // We do not detect `hehe` because guessing that the user did the wrong thing would require
      // us to run the unused name traverser defined in Typer (and hence proxy it from context util)
      avoidDCE
    }
  }

  locally {
    import sbt._, Def._
    def withKey(foo: => SettingKey[String]) = {
      Def.task { if (true) foo.value }
    }
    val foo = settingKey[String]("")
    withKey(foo)
  }

  locally {
    import sbt._
    import sbt.Def._
    val foo = settingKey[String]("")
    val condition = true
    val baz = Def.task[String] {
      // settings can be evaluated in a condition
      if (condition) foo.value
      else "..."
    }
  }

  locally {
    import sbt._
    import sbt.Def._
    val foo = settingKey[String]("")
    val baz = Def.task[Seq[String]] {
      (1 to 10).map(_ => foo.value)
    }
  }

  locally {
    import sbt._, Def._
    def withKey(bar: => SettingKey[Int]) = {
      Def.task {
        List(42).map { _ =>
          if (true) bar.value
        }
      }
    }
    val bar = settingKey[Int]("bar")
    withKey(bar)
  }
}
