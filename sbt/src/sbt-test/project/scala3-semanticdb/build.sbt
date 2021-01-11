ThisBuild / scalaVersion := "3.0.0-M2"
ThisBuild / semanticdbEnabled := true

lazy val check = taskKey[Unit]("check the Scala 3 semanticdb files")

Compile / check := checkTask("Main.semanticdb")
Test / check := checkTask("Test.semanticdb")

def checkTask(fileName: String): Def.Initialize[Task[Unit]] = Def.task {
  val _ = compile.value
  val dir = semanticdbTargetRoot.value
  assert(IO.listFiles(dir).exists(_.getName == fileName), s"$fileName is missing")
}