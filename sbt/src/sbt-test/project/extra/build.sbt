lazy val root = (project in file(".")).
  settings(
    autoScalaLibrary := false,
    libraryDependencies += "log4j" % "log4j" % "1.2.16",
    retrieveManaged := true,
    commands ++= Seq(
      addExtra("add1", addExtra1),
      addExtra("add2", addExtra2),
      checkExtra
    )
  )

def addExtra(name: String, f: (State, Seq[File]) => State) =
  Command.command(name) { s =>
    f(s, (file("lib_managed") ** "*.jar").get)
  }
def checkExtra =
  Command.command("check") { s =>
    val loader = Class.forName("org.apache.log4j.Level").getClassLoader
    val sbtLoader = classOf[sbt.internal.BuildDef].getClassLoader
    assert(loader eq sbtLoader, "Different loader for sbt and extra: " + sbtLoader + " and " + loader)
    s
  }
def addExtra1(s: State, extra: Seq[File]): State =
  {
    val cs = s.configuration.provider.components()
    val copied = cs.addToComponent("extra", extra.toArray)
    if(copied) s.reload else s
  }
def addExtra2(s: State, extra: Seq[File]): State =
  {
    val reload = State.defaultReload(s)
    val currentID = reload.app
    val currentExtra = currentID.classpathExtra
    val newExtra = (currentExtra ++ extra).distinct
    if(newExtra.length == currentExtra.length)
      s
    else
    {
      val newID = ApplicationID(currentID).copy(extra = extra)
      s.setNext(new State.Return(reload.copy(app = newID)))
    }
  }
