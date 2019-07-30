val generateSourceFile = taskKey[Unit]("generate source file")
generateSourceFile := {
  val testDir = ((Test / scalaSource).value.toPath / "Foo.scala").toString
  val content = s"object Foo { val x = 2 }"
  val src =
    s"""
       |import scala.language.experimental.macros
       |import scala.reflect.macros.blackbox
       |import java.nio.file.{ Files, Paths }
       |
       |object Generate {
       |  def gen: Unit = macro genImpl
       |  def genImpl(c: blackbox.Context): c.Expr[Unit] = {
       |    Files.write(Paths.get("${testDir.replace("\\", "\\\\")}"), "$content".getBytes)
       |    c.universe.reify(())
       |  }
       |}
       |""".stripMargin
  IO.write((Compile / scalaSource).value / "Generate.scala", src)
}

libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5" % "test"
