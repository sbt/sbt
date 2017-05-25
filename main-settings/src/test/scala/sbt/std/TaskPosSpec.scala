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
      if (condition) foo.value: @unchecked
      else bar.value: @unchecked
    }
  }
}
