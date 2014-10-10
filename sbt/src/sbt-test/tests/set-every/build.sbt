val a = project.settings(version := "2.8.1")

val trySetEvery = taskKey[Unit]("Tests \"set every\"")

trySetEvery := {
        val s = state.value
        val extracted = Project.extract(s)
        import extracted._
        val allProjs = structure.allProjectRefs
        val Some(aProj) = allProjs.find(_.project == "a")
        val aVer = (version in aProj get structure.data).get
        if (aVer != "1.0") {
          println("Version of project a: " + aVer + ", expected: 1.0")
          sys.error("\"set every\" did not change the version of all projects.")
        }
}
