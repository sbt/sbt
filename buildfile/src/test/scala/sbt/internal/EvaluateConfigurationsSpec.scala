package sbt.internal

import sbt.internal.parser.AbstractSpec
import sbt.internal.util.MessageOnlyException
import sbt.internal.inc.PlainVirtualFile
import sbt.io.IO
import java.nio.file.Paths
import java.io.File

object EvaluateConfigurationsSpec extends AbstractSpec {
  test("Specific error message for defining types") {
    // https://github.com/sbt/sbt/issues/9566

    val classpath = this.getClass.getClassLoader
      .asInstanceOf[java.net.URLClassLoader]
      .getURLs
      .map(_.toURI)
      .map(Paths.get)
      .toList

    IO.withTemporaryDirectory { tmpDir =>
      val sbtFile = new File(tmpDir, "my-build.sbt")
      IO.write(
        sbtFile,
        """|name := "foo"
           |
           |class A
           |""".stripMargin
      )
      try {
        EvaluateConfigurations.evaluateConfiguration(
          new Eval(Nil, classpath, None, None),
          PlainVirtualFile(sbtFile.toPath),
          Nil
        )
        fail()
      } catch {
        case error: MessageOnlyException =>
          assert(
            error.toString ==
              "my-build.sbt:3: Defining types in *.sbt file is not supported"
          )
      }
    }
  }
}
