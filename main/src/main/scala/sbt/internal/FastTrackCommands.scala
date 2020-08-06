/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import BasicCommandStrings._
import BasicCommands._
import BuiltinCommands.{ setTerminalCommand, shell, waitCmd }
import ContinuousCommands._

import sbt.internal.util.complete.Parser

/** This is used to speed up command parsing. */
private[sbt] object FastTrackCommands {
  private def fromCommand(
      cmd: String,
      command: Command,
      arguments: Boolean = true,
  ): (State, String) => Option[State] =
    (s, c) =>
      Parser.parse(if (arguments) c else "", command.parser(s)) match {
        case Right(newState) => Some(newState())
        case l               => None
      }
  private val commands = Map[String, (State, String) => Option[State]](
    FailureWall -> { case (s, c) => if (c == FailureWall) Some(s) else None },
    StashOnFailure -> fromCommand(StashOnFailure, stashOnFailure, arguments = false),
    PopOnFailure -> fromCommand(PopOnFailure, popOnFailure, arguments = false),
    Shell -> fromCommand(Shell, shell),
    SetTerminal -> fromCommand(SetTerminal, setTerminalCommand),
    failWatch -> fromCommand(failWatch, failWatchCommand),
    preWatch -> fromCommand(preWatch, preWatchCommand),
    postWatch -> fromCommand(postWatch, postWatchCommand),
    runWatch -> fromCommand(runWatch, runWatchCommand),
    stopWatch -> fromCommand(stopWatch, stopWatchCommand),
    waitWatch -> fromCommand(waitWatch, waitCmd),
  )
  private[sbt] def evaluate(state: State, cmd: String): Option[State] = {
    cmd.trim.split(" ") match {
      case Array(h, _*) =>
        commands.get(h) match {
          case Some(command) => command(state, cmd)
          case _             => None
        }
      case _ => None
    }
  }
}
