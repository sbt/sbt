/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

object AppendSpec {
  val onLoad = SettingKey[State => State]("onLoad")

  import Scope.Global
  import SlashSyntax0._

  def doSideEffect(): Unit = ()

  Global / onLoad := (Global / onLoad).value.andThen { s =>
    doSideEffect()
    s
  }

  Global / onLoad += { (s: State) =>
    doSideEffect()
    s
  }

  Global / onLoad += doSideEffect _
  Global / onLoad += (() => doSideEffect())
  Global / onLoad += (() => println("foo"))
}
