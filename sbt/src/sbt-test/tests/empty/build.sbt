testGrouping <<= (definedTests in Test) map { tests =>
      tests map { test =>
        new Tests.Group(
          name = test.name,
          tests = Seq(test),
          runPolicy = Tests.SubProcess(javaOptions = Seq.empty[String]))
      }
    }
