package sbt.internal.librarymanagement

import sbt.util.ShowLines

object TestShowLines:
  extension [A: ShowLines](a: A)
    inline def lines: Seq[String] =
      implicitly[ShowLines[A]].showLines(a)
end TestShowLines
