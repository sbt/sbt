/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.typesafe.com>
 */
package sbt
package server

trait Event

case class LogEvent() extends Event
case class StatusEvent() extends Event
case class ExecutionEvent() extends Event

trait Command

case class Execution(cmd: String) extends Command