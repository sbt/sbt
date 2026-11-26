/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

import java.nio.file.{ Files, Paths }

class ArgumentSpec extends org.specs2.mutable.Specification:
  "framework arguments" should:
    "run the chosen example" in:
      Files.writeString(Paths.get("chosen-ran"), "chosen")
      ok
    "run the other example" in:
      Files.writeString(Paths.get("other-ran"), "other")
      ok
end ArgumentSpec
