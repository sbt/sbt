package sbt

import java.io.File
import scala.io.Source

class NewFormatSpec extends AbstractSpec {
  implicit val splitter: SplitExpressions.SplitExpression = EvaluateConfigurations.splitExpressions

  "New Format " should {
    "Handle lines " in {
      val rootPath = getClass.getResource("").getPath + "../new-format/"
      println(s"Reading files from: $rootPath")
      val allFiles = new File(rootPath).listFiles.toList
      foreach(allFiles) {
        path =>
          println(s"$path")
          val lines = Source.fromFile(path).getLines().toList
          val (_, statements) = splitter(path, lines)
          statements.nonEmpty must be_==(true)
        //          orPending(s"""
        //                       |***should contains statements***
        //                       |$lines """.stripMargin)
      }
    }
  }

}
