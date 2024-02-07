import sbt.io.Using
import xsbti.compile.TastyFiles

ThisBuild / scalaVersion := "3.3.1"

TaskKey[Unit]("check") := {
  assert((Compile / auxiliaryClassFiles).value == Seq(TastyFiles.instance))
  assert((Test / auxiliaryClassFiles).value == Seq(TastyFiles.instance))
}

TaskKey[Unit]("check2") := checkTastyFiles(true, true).value

TaskKey[Unit]("check3") := checkTastyFiles(true, false).value

def checkTastyFiles(aExists: Boolean, bExists: Boolean) = Def.task {
  val p = (Compile / packageBin).value
  val c = fileConverter.value
  Using.jarFile(false)(c.toPath(p).toFile()): jar =>
    if aExists then assert(jar.getJarEntry("A.tasty") ne null)
    else assert(jar.getJarEntry("A.tasty") eq null)

    if bExists then assert(jar.getJarEntry("B.tasty") ne null)
    else assert(jar.getJarEntry("B.tasty") eq null)
}
