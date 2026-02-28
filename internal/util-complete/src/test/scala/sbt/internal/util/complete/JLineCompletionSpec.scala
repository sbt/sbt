/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util
package complete

import org.scalacheck.*
import org.scalacheck.Prop.*

object JLineCompletionSpec extends Properties("JLineCompletion"):

  property("case-insensitive completions are available at token start") =
    val commands = Set("testOnly", "testQuick", "compile", "clean")
    val parser = Parser.token(DefaultParsers.ID.examples(commands))
    val completions = Parser.completions(parser, "", 1).get
    val names = completions.map(_.append)
    (names.contains("testOnly")) :| "testOnly is in initial completions" &&
    (names.contains("compile")) :| "compile is in initial completions"

end JLineCompletionSpec
