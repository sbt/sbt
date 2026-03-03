import java.io.File
import scala.collection.mutable.ArrayBuffer
import sbt.io.IO

ThisBuild / scalaVersion := "2.12.8"
ThisBuild / organization := "com.example"
ThisBuild / resolvers +=
  MavenRepository("local-test-repo", (file("repo") / "").toURI.toASCIIString)

lazy val checkLog = taskKey[Unit]("")

libraryDependencies += "com.example.repo" % "a" % "1.0"

def findUpdateLogs(root: File): Vector[File] =
  if !root.exists then Vector.empty
  else
    val stack = ArrayBuffer(root)
    val logs = ArrayBuffer.empty[File]
    while stack.nonEmpty do
      val current = stack.remove(stack.length - 1)
      Option(current.listFiles).getOrElse(Array.empty[File]).foreach: child =>
        if child.isDirectory then stack += child
        else if child.isFile && child.getName == "out" then
          val normalized = child.getPath.replace('\\', '/')
          if normalized.contains("/streams/_global/update/_global/streams/out") then logs += child
    logs.toVector

checkLog := {
  val logs = findUpdateLogs(baseDirectory.value / "target" / "out" / "jvm")
  if logs.isEmpty then
    sys.error(s"Could not find update stream log under ${baseDirectory.value / "target" / "out" / "jvm"}")

  val contentByLog = logs.map(log => log -> IO.read(log))
  val combinedContent = contentByLog.map(_._2).mkString("\n")

  def assertContains(needle: String): Unit = {
    val found = contentByLog.exists(_._2.contains(needle))
    assert(
      found,
      s"""Missing '$needle' in update stream logs:
         |${contentByLog.map((log, _) => s"  - $log").mkString("\n")}
         |
         |Collected content:
         |$combinedContent
         |""".stripMargin
    )
  }

  assertContains("Note: Unresolved dependencies path:")
  assertContains("com.example.repo:missing:1.0")
  assertContains("com.example.repo:a:1.0")
  assertContains("com.example:")
}
