import complete.DefaultParsers._

InputKey[Unit]("check") <<= InputTask(_ => Space ~> IntBasic) { result => 
   (result, maxErrors) map { (expected, actual) =>
      assert(expected == actual, "Expected " + expected + ", got " + actual)
   }
}

