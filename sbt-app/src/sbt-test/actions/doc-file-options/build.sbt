val newContents = "bbbbbbbbb"

val rootContentFile = "root.txt"

ThisBuild / scalaVersion := "2.12.20"

lazy val root = (project in file("."))
  .settings(
    Compile / doc / scalacOptions := Seq("-doc-root-content", rootContentFile),
    TaskKey[Unit]("changeRootContent") := {
      IO.write(file(rootContentFile), newContents)
    },
    TaskKey[Unit]("check") := {
      val packageHtml = (Compile / doc / target).value / "index.html"
      assert(IO.read(packageHtml).contains(newContents), s"does not contains ${newContents} in ${packageHtml}" )
    }
  )
