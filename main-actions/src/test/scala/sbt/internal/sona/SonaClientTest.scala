/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.sona

import org.scalatest.flatspec.AnyFlatSpec
import sbt.internal.sona.SonaClientTest.RecordingLogger
import sbt.internal.util.BasicLogger
import sbt.util.*
import sjsonnew.support.scalajson.unsafe.Parser

import scala.collection.immutable

class SonaClientTest extends AnyFlatSpec {

  private def doTest(
      errorsJsonText: Option[String],
      expectedErrorMessage: String,
      expectedLogText: String = ""
  ): Unit = {
    val logger = new RecordingLogger()
    val errorsNode = errorsJsonText.map(Parser.parseUnsafe)
    val result = SonaClient.failedDeploymentErrorText(
      deploymentId = "12345",
      errors = errorsNode,
      log = logger
    )
    assert(result == expectedErrorMessage)

    val actualLogText = logger.getLogMessages.mkString("\n")
    assert(actualLogText == expectedLogText)

    () // to avoid the "discarded non-Unit" value warning
  }

  it should "construct a failed deployment error message without errors" in doTest(
    None,
    """deployment 12345 failed""".stripMargin
  )

  it should "construct a failed deployment error message with validation errors" in doTest(
    Some(
      """{
        |    "pkg:maven/org.example.company/sbt-plugin-core_2.12_1.0@0.0.6": [
        |      "Component with package url: 'pkg:maven/org.example.company/sbt-plugin-core_2.12_1.0@0.0.6' already exists"
        |    ],
        |    "pkg:maven/org.example.company/sbt-plugin-extra_2.12_1.0@0.0.6": [
        |      "Component with package url: 'pkg:maven/org.example.company/sbt-plugin-extra_2.12_1.0@0.0.6' some reason 1",
        |      "Component with package url: 'pkg:maven/org.example.company/sbt-plugin-extra_2.12_1.0@0.0.6' some reason 2"
        |    ]
        |}""".stripMargin
    ),
    """deployment 12345 failed with validation errors:
      |  - pkg:maven/org.example.company/sbt-plugin-core_2.12_1.0@0.0.6
      |    - Component with package url: 'pkg:maven/org.example.company/sbt-plugin-core_2.12_1.0@0.0.6' already exists
      |  - pkg:maven/org.example.company/sbt-plugin-extra_2.12_1.0@0.0.6
      |    - Component with package url: 'pkg:maven/org.example.company/sbt-plugin-extra_2.12_1.0@0.0.6' some reason 1
      |    - Component with package url: 'pkg:maven/org.example.company/sbt-plugin-extra_2.12_1.0@0.0.6' some reason 2""".stripMargin
  )

  it should "construct a failed deployment error message with validation errors in an unknown format" in doTest(
    Some(
      """[
        |  {
        |    "package" : "pkg:maven/org.example.company/sbt-plugin-core_2.12_1.0@0.0.6",
        |    "errors" : [
        |      "Component with package url: 'pkg:maven/org.example.company/sbt-plugin-core_2.12_1.0@0.0.6' already exists"
        |    ]
        |  },
        |  {
        |    "package" : "pkg:maven/org.example.company/sbt-plugin-extra_2.12_1.0@0.0.6",
        |    "errors" : [
        |      "Component with package url: 'pkg:maven/org.example.company/sbt-plugin-extra_2.12_1.0@0.0.6' some reason 1",
        |      "Component with package url: 'pkg:maven/org.example.company/sbt-plugin-extra_2.12_1.0@0.0.6' some reason 2"
        |    ]
        |  }
        |]""".stripMargin
    ),
    """deployment 12345 failed with validation errors:
      |[{
      |  "package": "pkg:maven/org.example.company/sbt-plugin-core_2.12_1.0@0.0.6",
      |  "errors": ["Component with package url: 'pkg:maven/org.example.company/sbt-plugin-core_2.12_1.0@0.0.6' already exists"]
      |}, {
      |  "package": "pkg:maven/org.example.company/sbt-plugin-extra_2.12_1.0@0.0.6",
      |  "errors": ["Component with package url: 'pkg:maven/org.example.company/sbt-plugin-extra_2.12_1.0@0.0.6' some reason 1", "Component with package url: 'pkg:maven/org.example.company/sbt-plugin-extra_2.12_1.0@0.0.6' some reason 2"]
      |}]""".stripMargin,
    expectedLogText =
      "[warn] Sonatype deployment validation errors JSON format has changed. Please update to the latest sbt version or report the issue to the sbt project"
  )
}

object SonaClientTest {

  implicit class RecordingLoggerOps(private val value: RecordingLogger) extends AnyVal {
    def getLogMessages: immutable.Seq[String] =
      value.getEvents.collect { case l: Log => s"[${l.level}] ${l.msg}" }
  }

  /**
   * Records logging events for later retrieval.
   *
   * @note This is a copy of a logger from the "util-logging" module tests.
   *       Instead of copying we could depend on the module test directly or extract it into some test-utilities module.
   */
  final class RecordingLogger extends BasicLogger {
    private var events: List[LogEvent] = Nil

    def getEvents = events.reverse
    def trace(t: => Throwable): Unit = { events ::= new Trace(t) }
    def log(level: Level.Value, message: => String): Unit = { events ::= new Log(level, message) }
    def success(message: => String): Unit = { events ::= new Success(message) }
    def logAll(es: Seq[LogEvent]): Unit = { events :::= es.toList }

    def control(event: ControlEvent.Value, message: => String): Unit =
      events ::= new ControlEvent(event, message)
  }
}
