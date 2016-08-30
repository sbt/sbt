import java.util.Locale

lazy val myTask = taskKey[String]("My task")
lazy val myInputTask = inputKey[String]("My input task")

lazy val root = project in file(".")
lazy val sub = project in file("sub")

def testTask[T](name: String, expected: String, task: TaskKey[T]) = TaskKey[Unit](name) := {
  val s = state.value
  val e = Project.extract(s)
  val (_, result) = e.runTask(task, s)
  if (expected != result) {
    throw sys.error(s"Error in test $name: Expected $expected but got $result")
  }
}

myTask := "root"
testTask("testRunTaskRoot", "root", myTask)

myTask in Compile := "root compile"
testTask("testRunTaskRootCompile", "root compile", myTask in Compile)

myTask in sub := "sub"
testTask("testRunTaskSub", "sub", myTask in sub)

myTask in (sub, Compile) := "sub compile"
testTask("testRunTaskSubCompile", "sub compile", myTask in (sub, Compile))

def argFunction(f: String => String) = Def.inputTask {
  import complete.Parsers._
  f((OptSpace ~> StringBasic).parsed)
}

def testInputTask[T](name: String, expected: String, task: InputKey[T], arg: String) = TaskKey[Unit](name) := {
  val s = state.value
  val e = Project.extract(s)
  val (_, result) = e.runInputTask(task, arg, s)
  if (expected != result) {
    throw sys.error(s"Error in test $name: Expected $expected but got $result")
  }
}

myInputTask := argFunction(_.toUpperCase(Locale.ENGLISH)).evaluated
testInputTask("testRunInputTaskRoot", "FOO", myInputTask, "foo")

myInputTask in Compile := argFunction(_.toLowerCase(Locale.ENGLISH)).evaluated
testInputTask("testRunInputTaskRootCompile", "foo", myInputTask in Compile, "FOO")

myInputTask in sub := argFunction(_.head.toString).evaluated
testInputTask("testRunInputTaskSub", "f", myInputTask in sub, "foo")

myInputTask in (sub, Compile) := argFunction(_.tail).evaluated
testInputTask("testRunInputTaskSubCompile", "oo", myInputTask in (sub, Compile), "foo")
