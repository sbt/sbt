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
import scala.util.control.NonFatal
import net.sf.cglib.proxy.Callback

private[sbt] object ForkTests
{
	def apply(runners: Map[TestFramework, Runner],  tests: List[TestDefinition], config: Execution, classpath: Seq[File], fork: ForkOptions, log: Logger, hideSuccessfulOutput: Boolean): Task[TestOutput]  = {
		val opts = processOptions(config, tests, log)

			import std.TaskExtra._
		val dummyLoader = this.getClass.getClassLoader // can't provide the loader for test classes, which is in another jvm
		def all(work: Seq[ClassLoader => Unit]) = work.fork(f => f(dummyLoader))

		val main =
			if(opts.tests.isEmpty)
				constant( TestOutput(TestResult.Passed, Map.empty[String, SuiteResult], Iterable.empty) )
			else
				mainTestTask(runners, opts, classpath, fork, log, config.parallel, hideSuccessfulOutput).tagw(config.tags: _*)
		main.dependsOn( all(opts.setup) : _*) flatMap { results =>
			all(opts.cleanup).join.map( _ => results)
		}
	}

	private[this] def mainTestTask(runners: Map[TestFramework, Runner], opts: ProcessedOptions, classpath: Seq[File], fork: ForkOptions, log: Logger, parallel: Boolean, hideSuccessfulOutput: Boolean): Task[TestOutput] =
		std.TaskExtra.task
		{
			val server = new ServerSocket(0)
			val testListeners = opts.testListeners flatMap {
				case tl: TestsListener => Some(tl)
				case _ => None
			}

			object Acceptor extends Runnable {
				val resultsAcc = mutable.Map.empty[String, SuiteReport]
				lazy val result = TestOutput(TestResult.overall(resultsAcc.values.map(_.result.result)), resultsAcc.mapValues(_.result).toMap, Iterable.empty)

				def run() {
					val socket =
						try {
							server.accept()
						} catch {
							case e: java.net.SocketException =>
							log.error("Could not accept connection from test agent: " + e.getClass + ": " + e.getMessage)
							log.trace(e)
							server.close()
							return
						}
					val os = new ObjectOutputStream(socket.getOutputStream)
					// Must flush the header that the constructor writes, otherwise the ObjectInputStream on the other end may block indefinitely
					os.flush()
					val is = new ObjectInputStream(socket.getInputStream)

					try {
						val config = new ForkConfiguration(log.ansiCodesSupported, parallel, hideSuccessfulOutput)
						os.writeObject(config)

						val taskdefs = opts.tests.map(t => new TaskDef(t.name, forkFingerprint(t.fingerprint), t.explicitlySpecified, t.selectors))
						os.writeObject(taskdefs.toArray)

						os.writeInt(runners.size)
						for ((testFramework, mainRunner) <- runners) {
							os.writeObject(testFramework.implClassNames.toArray)
							os.writeObject(mainRunner.args)
							os.writeObject(mainRunner.remoteArgs)
						}
						os.flush()

						new React(is, os, log, opts.testListeners, resultsAcc).react()
					} finally {
						try {
							is.close();	os.close(); socket.close()
						} catch {
							case NonFatal(e) => // swallow, we don't want to hide potential exceptions from above.
						}
					}
				}
			}

			try {
				testListeners.foreach(_.doInit())
				val acceptorThread = new Thread(Acceptor)
				acceptorThread.start()

				val fullCp = classpath ++: Seq(IO.classLocationFile[ForkMain], IO.classLocationFile[Framework], IO.classLocationFile[Callback])
				val options = Seq("-classpath", fullCp mkString File.pathSeparator, classOf[ForkMain].getCanonicalName, server.getLocalPort.toString)
				val ec = Fork.java(fork, options)
				log.debug(s"Forking tests: fork-options=$fork, extra-options=$options, parallel=$parallel, hide-successful-output=$hideSuccessfulOutput")
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
		}

	private[this] def forkFingerprint(f: Fingerprint): Fingerprint with Serializable =
		f match {
			case s: SubclassFingerprint => new ForkMain.SubclassFingerscan(s)
			case a: AnnotatedFingerprint => new ForkMain.AnnotatedFingerscan(a)
			case _ => sys.error("Unknown fingerprint type: " + f.getClass)
		}
}

// not thread-safe
private final class React(is: ObjectInputStream, os: ObjectOutputStream, log: Logger, listeners: Seq[TestReportListener], results: mutable.Map[String, SuiteReport])
{
	/** @return the existing or newly created suite with name `name` */
	private def getOrInitSuite(name: String) : SuiteReport = {
		results.get(name) match {
			case Some(existing) => existing
			case None =>
				val newSuite = SuiteReport.empty
				results += name -> newSuite
				newSuite
		}
	}
	
	private def updateSuite(name: String, update: SuiteReport => SuiteReport) {
		val existing = getOrInitSuite(name)
		val updated = update(existing)
		results += name -> updated
	}

	import ForkTags._
	@annotation.tailrec def react(): Unit = is.readObject match {
		case `Done` =>
			os.writeObject(Done)
			os.flush()

		case Array(`Error`, s: String) => log.error(s); react()
		case Array(`Warn`, s: String) => log.warn(s); react()
		case Array(`Info`, s: String) => log.info(s); react()
		case Array(`Debug`, s: String) => log.debug(s); react()

		case Array(`StartSuite`, name: String) =>
			getOrInitSuite(name)
			listeners.foreach( _.startSuite(name) )
			react()

		case Array(`EndTest`, suiteName: String, stdout: String, event: Event) =>
			log.debug("Received EndTest event with stdout: >" + stdout + "<")
			val testReport = TestReport(stdout,event)
			updateSuite(suiteName, _.addTest(testReport))
			listeners.foreach(_.endTest(testReport))
			react()
			
		case Array(`EndSuite`, name: String) =>
			val suite = getOrInitSuite(name)
			listeners.foreach(_.endSuite(name, suite))
			react()

		case Array(`EndSuiteError`, name: String, error: Throwable) =>
			val suite = getOrInitSuite(name)
			listeners.foreach(_.endSuite(name, error, Some(suite)))
			react()

		case t: Throwable =>
			log.trace(t)
			react()
	}
}
