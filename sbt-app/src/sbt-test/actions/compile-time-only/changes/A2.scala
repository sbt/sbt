import sbt.*
import Def.Initialize
import sbt.librarymanagement.LibraryManagementCodec.given

object A:
  val x1: Initialize[Task[Int]] = Def.task { 3 }
  val y1 = x1.value
