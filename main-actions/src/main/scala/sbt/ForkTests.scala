/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import scala.collection.mutable
import testing.{ Logger => _, _ }
import scala.util.control.NonFatal
import java.net.ServerSocket
import java.io._
import Tests.{ Output => TestOutput, _ }
import sbt.io.IO
import sbt.util.Logger
import sbt.ConcurrentRestrictions.Tag
import sbt.protocol.testing._

private[sbt] object ForkTests {
  def apply(runners: Map[TestFramework, Runner],
            tests: Vector[TestDefinition],
            config: Execution,
            classpath: Seq[File],
            fork: ForkOptions,
            log: Logger,
            tag: Tag): Task[TestOutput] = {
    val opts = processOptions(config, tests, log)

    import std.TaskExtra._
    val dummyLoader = this.getClass.getClassLoader // can't provide the loader for test classes, which is in another jvm
    def all(work: Seq[ClassLoader => Unit]) = work.fork(f => f(dummyLoader))

    val main =
      if (opts.tests.isEmpty)
        constant(TestOutput(TestResult.Passed, Map.empty[String, SuiteResult], Iterable.empty))
      else
        mainTestTask(runners, opts, classpath, fork, log, config.parallel).tagw(config.tags: _*)
    main.tag(tag).dependsOn(all(opts.setup): _*) flatMap { results =>
      all(opts.cleanup).join.map(_ => results)
    }
  }

  private[this] def mainTestTask(runners: Map[TestFramework, Runner],
                                 opts: ProcessedOptions,
                                 classpath: Seq[File],
                                 fork: ForkOptions,
                                 log: Logger,
                                 parallel: Boolean): Task[TestOutput] =
    std.TaskExtra.task {
      val server = new ServerSocket(0)
      val testListeners = opts.testListeners flatMap {
        case tl: TestsListener => Some(tl)
        case _                 => None
      }

      object Acceptor extends Runnable {
        val resultsAcc = mutable.Map.empty[String, SuiteResult]
        lazy val result =
          TestOutput(overall(resultsAcc.values.map(_.result)), resultsAcc.toMap, Iterable.empty)

        def run(): Unit = {
          val socket =
            try {
              server.accept()
            } catch {
              case e: java.net.SocketException =>
                log.error(
                  "Could not accept connection from test agent: " + e.getClass + ": " + e.getMessage)
                log.trace(e)
                server.close()
                return
            }
          val os = new ObjectOutputStream(socket.getOutputStream)
          // Must flush the header that the constructor writes, otherwise the ObjectInputStream on the other end may block indefinitely
          os.flush()
          val is = new ObjectInputStream(socket.getInputStream)

          try {
            val config = new ForkConfiguration(log.ansiCodesSupported, parallel)
            os.writeObject(config)

            val taskdefs = opts.tests.map(
              t =>
                new TaskDef(t.name,
                            forkFingerprint(t.fingerprint),
                            t.explicitlySpecified,
                            t.selectors))
            os.writeObject(taskdefs.toArray)

            os.writeInt(runners.size)
            for ((testFramework, mainRunner) <- runners) {
              os.writeObject(testFramework.implClassNames.toArray)
              os.writeObject(mainRunner.args)
              os.writeObject(mainRunner.remoteArgs)
            }
            os.flush()

            new React(is, os, log, opts.testListeners, resultsAcc).react()
          } catch {
            case NonFatal(e) =>
              def throwableToString(t: Throwable) = {
                import java.io._; val sw = new StringWriter; t.printStackTrace(new PrintWriter(sw));
                sw.toString
              }
              resultsAcc("Forked test harness failed: " + throwableToString(e)) = SuiteResult.Error
          } finally {
            is.close(); os.close(); socket.close()
          }
        }
      }

      try {
        testListeners.foreach(_.doInit())
        val acceptorThread = new Thread(Acceptor)
        acceptorThread.start()

        val fullCp = classpath ++: Seq(IO.classLocationFile[ForkMain],
                                       IO.classLocationFile[Framework])
        val options = Seq("-classpath",
                          fullCp mkString File.pathSeparator,
                          classOf[ForkMain].getCanonicalName,
                          server.getLocalPort.toString)
        val ec = Fork.java(fork, options)
        val result =
          if (ec != 0)
            TestOutput(TestResult.Error,
                       Map(
                         "Running java with options " + options
                           .mkString(" ") + " failed with exit code " + ec -> SuiteResult.Error),
                       Iterable.empty)
          else {
            // Need to wait acceptor thread to finish its business
            acceptorThread.join()
            Acceptor.result
          }

        testListeners.foreach(_.doComplete(result.overall))
        result
      } finally {
        server.close()
      }
    }

  private[this] def forkFingerprint(f: Fingerprint): Fingerprint with Serializable =
    f match {
      case s: SubclassFingerprint  => new ForkMain.SubclassFingerscan(s)
      case a: AnnotatedFingerprint => new ForkMain.AnnotatedFingerscan(a)
      case _                       => sys.error("Unknown fingerprint type: " + f.getClass)
    }
}
private final class React(is: ObjectInputStream,
                          os: ObjectOutputStream,
                          log: Logger,
                          listeners: Seq[TestReportListener],
                          results: mutable.Map[String, SuiteResult]) {
  import ForkTags._
  @annotation.tailrec
  def react(): Unit = is.readObject match {
    case `Done` =>
      os.writeObject(Done); os.flush()
    case Array(`Error`, s: String) =>
      log.error(s); react()
    case Array(`Warn`, s: String) =>
      log.warn(s); react()
    case Array(`Info`, s: String) =>
      log.info(s); react()
    case Array(`Debug`, s: String) =>
      log.debug(s); react()
    case t: Throwable =>
      log.trace(t); react()
    case Array(group: String, tEvents: Array[Event]) =>
      listeners.foreach(_ startGroup group)
      val event = TestEvent(tEvents)
      listeners.foreach(_ testEvent event)
      val suiteResult = SuiteResult(tEvents)
      results += group -> suiteResult
      listeners.foreach(_ endGroup (group, suiteResult.result))
      react()
  }
}
