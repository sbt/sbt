import complete._
import complete.DefaultParsers._

lazy val root = (project in file(".")).
  settings(
    externalPom(),
    scalaVersion := "2.9.0-1",
    check := checkTask.evaluated,
    managedClasspath in Provided := Classpaths.managedJars(Provided, classpathTypes.value, update.value)
  )

def checkTask = Def.inputTask {
  val result = parser.parsed
  val (conf, names) = result
  println("Checking: " + conf.name)
  checkClasspath(conf match {
    case Provided => managedClasspath in Provided value
    case Compile  => fullClasspath in Compile value
    case Test     => fullClasspath in Test value
    case Runtime  => fullClasspath in Runtime value
  }, names.toSet)
}

lazy val check = InputKey[Unit]("check")
def parser: Parser[(Configuration,Seq[String])] = (Space ~> token(cp(Compile) | cp(Runtime) | cp(Provided) | cp(Test))) ~ spaceDelimited("<module-names>")
def cp(c: Configuration): Parser[Configuration] = c.name ^^^ c
def checkClasspath(cp: Seq[Attributed[File]], names: Set[String]) =
{
  val fs = cp.files filter { _.getName endsWith ".jar" }
  val intersect = fs filter { f => names exists { f.getName startsWith _ } }
  assert(intersect == fs, "Expected:" + seqStr(names.toSeq) + "Got: " + seqStr(fs))
  ()
}
def seqStr(s: Seq[_]) = s.mkString("\n\t", "\n\t", "\n")
