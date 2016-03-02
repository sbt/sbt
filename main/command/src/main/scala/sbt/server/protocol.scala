/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.typesafe.com>
 */
package sbt
package server

/*
 * These classes are the protocol for client-server interaction,
 * commands can come from the client side, while events are emitted
 * from sbt to inform the client of state changes etc.
 */
private[sbt] sealed trait Event

private[sbt] final case class LogEvent(level: String, message: String) extends Event

sealed trait Status
private[sbt] final case object Ready extends Status
private[sbt] final case class Processing(command: String, commandQueue: Seq[String]) extends Status

private[sbt] final case class StatusEvent(status: Status) extends Event
private[sbt] final case class ExecutionEvent(command: String, success: Boolean) extends Event

private[sbt] sealed trait Command

private[sbt] final case class Execution(cmd: String) extends Command