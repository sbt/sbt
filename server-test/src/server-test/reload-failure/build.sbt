/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

val readInput = taskKey[Unit]("Read the registered input file")

readInput / fileInputs := (baseDirectory.value.toGlob / "input") :: Nil
readInput := {
  val contents = readInput.inputFiles.map(path => IO.read(path.toFile))
  IO.write(baseDirectory.value / "read-input", contents.mkString)
}

Global / onLoad := {
  val previous = (Global / onLoad).value
  val base = (LocalRootProject / baseDirectory).value
  state => {
    val fail = base / "fail-load"
    if (fail.exists) {
      IO.delete(fail)
      sys.error("build load hook failed")
    }
    previous(state)
  }
}
