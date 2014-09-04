package sbt

abstract class CheckIfParsedSpec(implicit val splitter: SplitExpressions.SplitExpression = EvaluateConfigurations.splitExpressions) extends AbstractSpec {

  this.getClass.getName should {

    "Parse sbt file " in {
      foreach(files) {
        case (content, description, nonEmptyImports, nonEmptyStatements) =>
          println(s"""${getClass.getSimpleName}: "$description" """)
          val (imports, statements) = split(content)

          statements.nonEmpty must be_==(nonEmptyStatements)
          //          orPending(s"""$description
          //                       |***${shouldContains(nonEmptyStatements)} statements***
          //                       |$content """.stripMargin)

          imports.nonEmpty must be_==(nonEmptyImports)
        //          orPending(s"""$description
        //                       |***${shouldContains(nonEmptyImports)} imports***
        //                       |$content """.stripMargin)
      }
    }
  }

  //  private def shouldContains(b: Boolean) = s"""Should ${
  //    if (b) {
  //      "contain"
  //    } else {
  //      "not contain"
  //    }
  //  }"""

  protected def files: Seq[(String, String, Boolean, Boolean)]

}
