package sbt
package nio

import java.nio.file._
import sbt.nio.Keys._

object Stamps {
  def check(key: TaskKey[_]): Def.Initialize[Task[Unit]] = Def.task {
    (key / inputFileStamps).value.map {
      case (p, FileStamp.Hash(_)) =>
      case (p, _) =>
        Files.write(p, "fail".getBytes)
        throw new IllegalStateException("Got incorrect stamp: $s")
    }
  }
}