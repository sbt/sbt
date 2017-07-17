lazy val Custom = config("custom")

lazy val root = (project in file(".")).
  configs(Custom).
  settings(
    TaskKey[Unit]("checkPom") := checkPom.value,
    libraryDependencies ++= Seq(
      "a" % "a" % "1.0",
      "b" % "b" % "1.0" % "runtime,optional",
      "c" % "c" % "1.0" % "optional",
      "d" % "d" % "1.0" % "test",
      "e" % "e" % "1.0" % Custom,
      "f" % "f" % "1.0" % "custom,optional,runtime",
      "g" % "g" % "1.0" % "custom,runtime" classifier "foo",
      "h" % "h" % "1.0" % "custom,optional,runtime" classifier "foo"
    )
  )

def checkPom = makePom map { pom =>
  val expected = Seq(
    ("a", None, false, None),
    ("b", Some("runtime"), true, None),
    ("c", None, true, None),
    ("d", Some("test"), false, None),
    ("e", None, false, None),
    ("f", Some("runtime"), true, None),
    ("g", Some("runtime"), false, Some("foo")),
    ("h", Some("runtime"), true, Some("foo"))
  )
  val loaded = xml.XML.loadFile(pom)
  val deps = loaded \\ "dependency"
  expected foreach { case (id, scope, opt, classifier) =>
    val dep = deps.find(d => (d \ "artifactId").text == id).getOrElse( sys.error("Dependency '" + id + "' not written to pom:\n" + loaded))
    val actualOpt = java.lang.Boolean.parseBoolean( (dep \\ "optional").text )
    assert(opt == actualOpt, "Invalid 'optional' section '" + (dep \\ "optional") + "' for " + id + ", expected optional=" + opt)

    val actualScope = (dep \\ "scope") match { case Seq() => None; case x => Some(x.text) }
    val actualClassifier = (dep \\ "classifier") match { case Seq() => None; case x => Some(x.text) }
    assert(actualScope == scope, "Invalid 'scope' section '" + (dep \\ "scope") + "' for " + id + ", expected scope=" + scope)
    assert(actualClassifier == classifier, "Invalid 'classifier' section '" + (dep \\ "classifier") + "' for " + id + ", expected classifier=" + classifier)
  }
}
