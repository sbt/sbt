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

Compile / myTask := "root compile"
testTask("testRunTaskRootCompile", "root compile", Compile / myTask)

sub / myTask := "sub"
testTask("testRunTaskSub", "sub", sub / myTask)

sub / Compile / myTask := "sub compile"
testTask("testRunTaskSubCompile", "sub compile", sub / Compile / myTask)

def argFunction(f: String => String) = Def.inputTask {
  import complete.Parsers._
  f((OptSpace ~> StringBasic).parsed)
}

def testInputTask[T](name: String, expected: String, task: InputKey[T], arg: String) =
  TaskKey[Unit](name) := {
    val s = state.value
    val e = Project.extract(s)
    val (_, result) = e.runInputTask(task, arg, s)
    if (expected != result) {
      throw sys.error(s"Error in test $name: Expected $expected but got $result")
    }
  }

myInputTask := argFunction(_.toUpperCase(Locale.ENGLISH)).evaluated
testInputTask("testRunInputTaskRoot", "FOO", myInputTask, "foo")

Compile / myInputTask := argFunction(_.toLowerCase(Locale.ENGLISH)).evaluated
testInputTask("testRunInputTaskRootCompile", "foo", Compile / myInputTask, "FOO")

sub / myInputTask := argFunction(_.head.toString).evaluated
testInputTask("testRunInputTaskSub", "f", sub / myInputTask, "foo")

sub / Compile / myInputTask := argFunction(_.tail).evaluated
testInputTask("testRunInputTaskSubCompile", "oo", sub / Compile / myInputTask, "foo")
