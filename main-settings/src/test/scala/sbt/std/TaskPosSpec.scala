/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.std

class TaskPosSpec {
  // Starting sbt 1.4.0, Def.task can have task value lookups inside
  // if branches since tasks with single if-expressions are automatically
  // converted into a conditional task.
  locally {
    import sbt.*, Def.*
    val foo = taskKey[String]("")
    val bar = taskKey[String]("")
    val condition = true
    Def.task[String] {
      if (condition) foo.value
      else bar.value
    }
  }

  // Dynamic tasks can have task invocations inside if branches
  locally {
    import sbt.*, Def.*
    val foo = taskKey[String]("")
    val bar = taskKey[String]("")
    val condition = true
    Def.taskDyn[String] {
      if (condition) foo
      else bar
    }
  }

  // Dynamic settings can have setting invocations inside if branches
  locally {
    import sbt.*, Def.*
    val foo = settingKey[String]("")
    val bar = settingKey[String]("")
    val condition = true
    Def.settingDyn[String] {
      if (condition) foo
      else bar
    }
  }

  locally {
    import sbt.*, Def.*
    val foo = taskKey[String]("")
    val condition = true
    Def.task[String] {
      val fooAnon = () => foo.value: @sbtUnchecked
      if (condition) fooAnon()
      else fooAnon()
    }
  }

  locally {
    import sbt.*, Def.*
    val foo = taskKey[String]("")
    val condition = true
    Def.task[String] {
      val fooAnon = () => (foo.value: @sbtUnchecked) + ""
      if (condition) fooAnon()
      else fooAnon()
    }
  }

  locally {
    import sbt.*, Def.*
    val foo = taskKey[String]("")
    val bar = taskKey[String]("")
    val condition = true
    Def.task[String] {
      if (condition) foo.value: @sbtUnchecked
      else bar.value: @sbtUnchecked
    }
  }

  locally {
    // This is fix 1 for appearance of tasks inside anons
    import sbt.*, Def.*
    val foo = taskKey[String]("")
    val condition = true
    Def.task[String] {
      val fooResult = foo.value
      val anon = () => fooResult + " "
      if (condition) anon()
      else ""
    }
  }

  locally {
    // This is fix 2 for appearance of tasks inside anons
    import sbt.*, Def.*
    val foo = taskKey[String]("")
    val condition = true
    Def.taskDyn[String] {
      val anon1 = (value: String) => value + " "
      if (condition) {
        Def.task(anon1(foo.value))
      } else Def.task("")
    }
  }

  locally {
    // missing .value error should not happen inside task dyn
    import sbt.*, Def.*
    val foo = taskKey[String]("")
    Def.taskDyn[String] {
      foo
    }
  }

  locally {
    import sbt.*, Def.*
    val foo = taskKey[String]("")
    val avoidDCE = ""
    Def.task[String] {
      val _ = foo: @sbtUnchecked
      avoidDCE
    }
  }

  locally {
    import sbt.*, Def.*
    val foo = taskKey[String]("")
    Def.task[String] {
      def inner(s: KeyedInitialize[?]) = println(s)
      inner(foo)
      ""
    }
  }

  locally {
    // In theory, this should be reported, but missing .value analysis is dumb at the cost of speed
    import sbt.*, Def.*
    val foo = taskKey[String]("")
    def avoidDCE = { println(""); "" }
    Def.task[String] {
      val (_, _) = "" match {
        case _ => (foo, 1 + 2)
      }
      avoidDCE
    }
  }

  locally {
    import sbt.*, Def.*
    val foo = taskKey[String]("")
    def avoidDCE(x: TaskKey[String]) = x.toString
    Def.task[String] {
      val hehe = foo
      // We do not detect `hehe` because guessing that the user did the wrong thing would require
      // us to run the unused name traverser defined in Typer (and hence proxy it from context util)
      avoidDCE(hehe)
    }
  }

  locally {
    import sbt.*, Def.*
    def withKey(foo: => SettingKey[String]): Def.Initialize[Task[Unit]] = {
      Def.task {
        if (true) {
          Def.unit(foo.value); ()
        }
      }
    }
    val foo = settingKey[String]("")
    withKey(foo)
  }

  locally {
    import sbt.*, Def.*
    val foo = settingKey[String]("")
    val condition = true
    Def.task[String] {
      // settings can be evaluated in a condition
      if (condition) foo.value
      else "..."
    }
  }

  locally {
    import sbt.*, Def.*
    val foo = settingKey[String]("")
    Def.task[Seq[String]] {
      (1 to 10).map(_ => foo.value)
    }
  }

  locally {
    import sbt.*, Def.*
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
