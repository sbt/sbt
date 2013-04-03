/* sbt -- Simple Build Tool
 * Copyright 2012 Eugene Vigdorchik
 */
package sbt

import scala.collection.mutable
import testing._
import java.net.ServerSocket
import java.io._
import Tests.{Output => TestOutput, _}
import ForkMain._

private[sbt] object ForkTests {
	def apply(frameworks: Seq[TestFramework], tests: List[TestDefinition], config: Execution, classpath: Seq[File], fork: ForkOptions, log: Logger): Task[TestOutput]  = {
		val opts = config.options.toList
		val listeners = opts flatMap {
			case Listeners(ls) => ls
			case _ => Nil
		}
		val testListeners = listeners flatMap {
			case tl: TestsListener => Some(tl)
			case _ => None
		}
		val filters = opts flatMap {
			case Filter(f) => Some(f)
			case _ => None
		}
		val argMap = frameworks.map {
			f => f.implClassName -> opts.flatMap {
				case Argument(None | Some(`f`), args) => args
				case _ => Nil
			}
		}.toMap

		std.TaskExtra.task {
			if (!tests.isEmpty) {
				val server = new ServerSocket(0)
				object Acceptor extends Runnable {
					val resultsAcc = mutable.Map.empty[String, TestResult.Value]
					lazy val result = (overall(resultsAcc.values), resultsAcc.toMap)
					def run: Unit = {
						val socket =
							try {
								server.accept()
							} catch {
								case _: java.net.SocketException => return
							}
						val os = new ObjectOutputStream(socket.getOutputStream)
						val is = new ObjectInputStream(socket.getInputStream)

						try {
							os.writeBoolean(log.ansiCodesSupported)
							
							val testsFiltered = tests.filter(test => filters.forall(_(test.name))).map{
								t => new ForkTestDefinition(t.name, t.fingerprint)
							}.toArray
							os.writeObject(testsFiltered)

							os.writeInt(frameworks.size)
							for ((clazz, args) <- argMap) {
								os.writeObject(clazz)
								os.writeObject(args.toArray)
							}
							os.flush()

							(new React(is, os, log, listeners, resultsAcc)).react()
						} finally {
							is.close();	os.close(); socket.close()
						}
					}
				}

				try {
					testListeners.foreach(_.doInit())
					new Thread(Acceptor).start()

					val fullCp = classpath ++: Seq(IO.classLocationFile[ForkMain], IO.classLocationFile[Framework])
					val options = Seq("-classpath", fullCp mkString File.pathSeparator, classOf[ForkMain].getCanonicalName, server.getLocalPort.toString)
					val ec = Fork.java(fork, options)
					val result =
						if (ec != 0)
							(TestResult.Error, Map("Running java with options " + options.mkString(" ") + " failed with exit code " + ec -> TestResult.Error))
						else
							Acceptor.result
					testListeners.foreach(_.doComplete(result._1))
					result
				} finally {
					server.close()
				}
			} else
				(TestResult.Passed, Map.empty[String, TestResult.Value])
		} tagw (config.tags: _*)
	}
}
private final class React(is: ObjectInputStream, os: ObjectOutputStream, log: Logger, listeners: Seq[TestReportListener], results: mutable.Map[String, TestResult.Value])
{
	import ForkTags._
	@annotation.tailrec def react(): Unit = is.readObject match {
		case `Done` => os.writeObject(Done); os.flush()
		case Array(`Error`, s: String) => log.error(s); react()
		case Array(`Warn`, s: String) => log.warn(s); react()
		case Array(`Info`, s: String) => log.info(s); react()
		case Array(`Debug`, s: String) => log.debug(s); react()
		case t: Throwable => log.trace(t); react()
		case Array(group: String, tEvents: Array[Event]) =>
			listeners.foreach(_ startGroup group)
			val event = TestEvent(tEvents)
			listeners.foreach(_ testEvent event)
			val result = event.result getOrElse TestResult.Passed
			results += group -> result
			listeners.foreach(_ endGroup (group, result))
			react()
	}
}
