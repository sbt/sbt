import sbt.nio.Keys._

val fileInputTask = taskKey[Unit]("task with file inputs")

fileInputTask / fileInputs += (baseDirectory.value / "base").toGlob / "*.md"

fileInputTask / inputFileStamper := sbt.nio.FileStamper.LastModified

fileInputTask := Def.taskDyn {
  (fileInputTask / changedInputFiles).value match {
    case Some(ChangedFiles(_, _, u)) if u.nonEmpty => Def.task(assert(true))
    case None => Def.task(assert(false))
  }
}.value

val setLastModified = taskKey[Unit]("Reset the last modified time")
setLastModified := {
  val file = baseDirectory.value / "base" / "Bar.md"
  IO.setModifiedTimeOrFalse(file, 1234567890L)
}
