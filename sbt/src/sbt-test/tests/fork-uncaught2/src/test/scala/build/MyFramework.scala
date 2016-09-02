package build

import _root_.sbt.testing._

class MyFramework extends sbt.testing.Framework {
  def fingerprints = Array(new AnnotatedFingerprint { def isModule = true; def annotationName = "my" })
  def name = "my"
  def runner(args: Array[String], remoteArgs: Array[String], testClassLoader: ClassLoader): Runner =
    new MyRunner(args, remoteArgs, testClassLoader)
}

class MyRunner(val args: Array[String], val remoteArgs: Array[String],
               val testClassLoader: ClassLoader) extends sbt.testing.Runner {
  def tasks(taskDefs: Array[TaskDef]): Array[Task] = throw new Throwable()
  def done(): String = ""
}

