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
	def apply(runners: Map[TestFramework, Runner],  tests: List[TestDefinition], config: Execution, loader: ClassLoader, classpath: Seq[File], fork: ForkOptions, log: Logger): Task[TestOutput]  = {
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

		std.TaskExtra.task {
			if (!tests.isEmpty) {
				val server = new ServerSocket(0)
				object Acceptor extends Runnable {
					val resultsAcc = mutable.Map.empty[String, SuiteResult]
					lazy val result = TestOutput(overall(resultsAcc.values.map(_.result)), resultsAcc.toMap, Iterable.empty)
					def run: Unit = {
						val socket =
							try {
								server.accept()
							} catch {
								case _: java.net.SocketException => return
							}
						val os = new ObjectOutputStream(socket.getOutputStream)
						// Make sure that ObjectInputStream use the passed in class loader
						// ObjectInputStream class loading seems to be confusing, some old but useful links for reference:
						// https://forums.oracle.com/thread/1151865
						// http://sourceforge.net/p/jpype/bugs/52/
						// http://tech-tauk.blogspot.com/2010/05/thread-context-classlaoder-in.html
						val is = new ObjectInputStream(socket.getInputStream) {
							override protected def resolveClass(desc: ObjectStreamClass): Class[_] = {
								try {
									val name = desc.getName
									Class.forName(name, false, loader)
								}
								catch {
									case e: ClassNotFoundException => super.resolveClass(desc)
								}
							}
						}

						try {
							os.writeBoolean(log.ansiCodesSupported)
							
							val testsFiltered = tests.filter(test => filters.forall(_(test.name))).map{
								t => new ForkTestDefinition(t.name, t.fingerprint, t.explicitlySpecified, t.selectors)
							}.toArray
							os.writeObject(testsFiltered)

							os.writeInt(runners.size)
							for ((testFramework, mainRunner) <- runners) {
								val remoteArgs = mainRunner.remoteArgs()
								os.writeObject(testFramework.implClassNames.toArray)
								os.writeObject(mainRunner.args)
								os.writeObject(remoteArgs)
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
					val acceptorThread = new Thread(Acceptor)
					acceptorThread.start()

					val fullCp = classpath ++: Seq(IO.classLocationFile[ForkMain], IO.classLocationFile[Framework])
					val options = Seq("-classpath", fullCp mkString File.pathSeparator, classOf[ForkMain].getCanonicalName, server.getLocalPort.toString)
					val ec = Fork.java(fork, options)
					val result =
						if (ec != 0)
							TestOutput(TestResult.Error, Map("Running java with options " + options.mkString(" ") + " failed with exit code " + ec -> SuiteResult.Error), Iterable.empty)
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
			} else
				TestOutput(TestResult.Passed, Map.empty[String, SuiteResult], Iterable.empty)
		} tagw (config.tags: _*)
	}
}
private final class React(is: ObjectInputStream, os: ObjectOutputStream, log: Logger, listeners: Seq[TestReportListener], results: mutable.Map[String, SuiteResult])
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
			val suiteResult = SuiteResult(tEvents)
			results += group -> suiteResult
			listeners.foreach(_ endGroup (group, suiteResult.result))
			react()
	}
}
