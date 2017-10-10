/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.test

import java.io.File
import sjsonnew._, BasicJsonProtocol._
import sbt.Def.{ Setting, inputKey, settingKey, taskKey }
import sbt.Scope.Global
import sbt.librarymanagement.ModuleID
import sbt.librarymanagement.syntax._
import sbt.{ LocalProject, ProjectReference, ThisBuild, Zero }

object SlashSyntaxTest extends sbt.SlashSyntax {
  final case class Proj(id: String)
  implicit def projToRef(p: Proj): ProjectReference = LocalProject(p.id)

  val projA = Proj("a")

  val cancelable = settingKey[Boolean]("")
  val console = taskKey[Unit]("")
  val libraryDependencies = settingKey[Seq[ModuleID]]("")
  val name = settingKey[String]("")
  val run = inputKey[Unit]("")
  val scalaVersion = settingKey[String]("")
  val scalacOptions = taskKey[Seq[String]]("")

  val foo = taskKey[Int]("")
  val bar = taskKey[Int]("")
  val baz = inputKey[Unit]("")
  val buildInfo = taskKey[Seq[File]]("")

  val uTest = "com.lihaoyi" %% "utest" % "0.5.3"

  Seq[Setting[_]](
    Global / cancelable := true,
    ThisBuild / scalaVersion := "2.12.3",
    console / scalacOptions += "-deprecation",
    Compile / console / scalacOptions += "-Ywarn-numeric-widen",
    projA / Compile / console / scalacOptions += "-feature",
    Zero / name := "foo",
    Zero / Zero / name := "foo",
    Zero / Zero / Zero / name := "foo",
    Test / bar := 1,
    Test / foo := (Test / bar).value + 1,
    Compile / foo := {
      (Compile / bar).previous.getOrElse(1)
    },
    Compile / bar := {
      (Compile / foo).previous.getOrElse(2)
    },
    Test / buildInfo := Nil,
    baz := {
      val _ = (Test / buildInfo).taskValue
      (Compile / run).evaluated
    },
    foo := (Test / bar).value + 1,
    libraryDependencies += uTest % Test,
  )
}
