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
}
