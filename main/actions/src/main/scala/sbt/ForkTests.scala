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
import java.util.concurrent.ConcurrentLinkedQueue
import scala.annotation.tailrec
import java.util.concurrent.atomic.AtomicReference

private[sbt] object ForkTests
{
	/**
	 * @param hideSuccessfulOutput only available in forked mode
	 * @param noForkedVm only available in forked mode; if > 1 will disable intra-vm parallel execution
	 */
	def apply(frameworks: Map[TestFramework,Framework], runners: Map[TestFramework, Runner],  tests: List[TestDefinition], config: Execution, classpath: Seq[File], fork: ForkOptions, log: Logger, hideSuccessfulOutput: Boolean, noForkedVm: Int): Task[TestOutput]  = {
		val opts = processOptions(config, tests, log)

			import std.TaskExtra._
		val dummyLoader = this.getClass.getClassLoader // can't provide the loader for test classes, which is in another jvm
		def all(work: Seq[ClassLoader => Unit]) = work.fork(f => f(dummyLoader))

		val main =
			if(opts.tests.isEmpty)
				constant( TestOutput(TestResult.Passed, Map.empty[String, SuiteResult], Iterable.empty) )
			else
				mainTestTask(frameworks, runners, opts, classpath, fork, log, config.parallel, hideSuccessfulOutput, noForkedVm).tagw(config.tags: _*)
		main.dependsOn( all(opts.setup) : _*) flatMap { results =>
			all(opts.cleanup).join.map( _ => results)
		}
	}

	// Note: these log-related utilities could be made more visible, somewhere else?

	private sealed trait LoggerEvent
	private case class TraceEvent(error: Throwable) extends LoggerEvent
	private case class SuccessEvent(message: String) extends LoggerEvent
	private case class LogEvent(level: Level.Value, message: String) extends LoggerEvent

	private class RecordingLogger extends Logger {
		private val events = new AtomicReference[List[LoggerEvent]](Nil)

		@tailrec private def addEvent(e: LoggerEvent) {
			val previous = events.get
			if( !events.compareAndSet(previous, e :: previous) ) {
				addEvent(e)
			}
		}

		def getEvents : List[LoggerEvent] = events.get.reverse
		def getEventsAndReset : List[LoggerEvent] = events.getAndSet(Nil).reverse

		def trace(t: => Throwable) { addEvent(new TraceEvent(t))}
		def success(message: => String) { addEvent(new SuccessEvent(message))}
		def log(level: Level.Value, message: => String) { addEvent(new LogEvent(level, message))}
	}

	/**
	 * A test runner in a forked VM.
	 *
	 * Picks tasks from `suiteQueue` and send them to the forked VM for execution. Once the queue is empty, shuts down the VM.
	 * If an unexpected failure happen (i.e not a test failure), the VM will be terminated. Terminated VMs (could be a test calling `System.exit`)
	 * are not restarted.
	 *
	 * @param parallel whether tests should run in parallel in the forked VM - this will be disabled if usePipedProcessOutput is enabled
	 * @param usePipedProcessOutput `true` to use the process output for the test report stdout, `false` to let the VM capture the standard output and
	 *                             send it over the socket.
	 */
	class ForkedTestRunner(runners: Map[TestFramework, Runner],
								 classpath: Seq[File],
								 forkOptions: ForkOptions,
								 log: Logger,
								 listeners: Seq[TestReportListener],
								 parallel: Boolean,
								 hideSuccessfulOutput: Boolean,
								 usePipedProcessOutput: Boolean,
								 suiteQueue: ConcurrentLinkedQueue[ForkSuites]) extends Runnable {

		private val server = new ServerSocket(0)
		private var process : Process = _
		private var vmArgs : Seq[String] = Nil
		private val thread = new Thread(this)

		/** make sure we don't activate intra-vm parallelism when `usePipedProcessOutput` is enabled */
		private val parallelSafe = parallel && !usePipedProcessOutput

		/** a map of [SuiteName, SuiteReport] */
		val results = mutable.Map.empty[String, SuiteReport]

		private val outputBuffer = new RecordingLogger

		def getLocalPort : Int = server.getLocalPort

		private def closeSocketQuietly() {
			try {
				server.close()
			} catch {
				case _ : Throwable =>
			}
		}

		/**
		 * Start the forked VM and start sending tests to it from the `suiteQueue`, through the socket.
		 * A call to start should be followed by a call to `join`.
		 * Upon failure the associated resources will be released (thread & socket).
		 * This method does not block and should only be called once.
		 */
		def start() {
			try {
				thread.start() 
	
				val fullCp = classpath ++: Seq(IO.classLocationFile[ForkMain], IO.classLocationFile[Framework], IO.classLocationFile[Callback])
				vmArgs = Seq("-classpath", fullCp mkString File.pathSeparator, classOf[ForkMain].getCanonicalName, getLocalPort.toString)
	
				log.debug(s"Forking tests: fork-options=$forkOptions, vm-args=$vmArgs, parallel=$parallelSafe, " +
					s"hide-successful-output=$hideSuccessfulOutput, usePipedProcessOutput=$usePipedProcessOutput")

				val newOptions = if( usePipedProcessOutput ) {
					val outputStrategy = if( hideSuccessfulOutput ) {
						BufferedOutput(outputBuffer)
					} else {
						LoggedOutput(new MultiLogger(List(FullLogger(log), FullLogger(outputBuffer))))
					}
					forkOptions.copy(outputStrategy = Some(outputStrategy))
				} else {
					forkOptions
				}

				process = new Fork("java", None).fork(newOptions, vmArgs)
			} catch {
				case err : Throwable =>
					// if the process has not been created, then the thread will be released
					// when closing the socket
					closeSocketQuietly()
					throw err
			}
		}

		private def getPipedProcessOutputAndReset() : String = {
			val events = outputBuffer.getEventsAndReset
			events.flatMap {
				case LogEvent(Level.Info, msg) => Some(msg)
				case _ => None
			}.mkString("\n")
		}

		/** Wait until the VM dies */
		def join() {
			try {
				thread.join()
				if( process != null ) {
					val exitCode = process.exitValue()
					if( exitCode != 0 ) {
						throw new RuntimeException("Running java with options " + vmArgs.mkString(" ") + " failed with exit code " + exitCode)
					}
				}
			} finally {
				closeSocketQuietly()
			}
		}

		def run() {
			val socket =
				try {
					server.accept()
				} catch {
					case e: java.net.SocketException =>
						log.error("Could not accept connection from test agent: " + e.getClass + ": " + e.getMessage)
						log.trace(e)
						closeSocketQuietly()
						return
				}

			val os = new ObjectOutputStream(socket.getOutputStream)
			// Must flush the header that the constructor writes, otherwise the ObjectInputStream on the other end may block indefinitely
			os.flush()
			val is = new ObjectInputStream(socket.getInputStream)

			try {
				writeConfig(os)
				writeTestFrameworks(os)
				writeTestSuite(os, is)
				
			} finally {
				try {
					is.close();	os.close(); socket.close()
				} catch {
					case NonFatal(e) => // swallow, we don't want to hide potential exceptions from above.
				}
			}
		}

		private def writeConfig(os: ObjectOutputStream) {
			val config = new ForkConfiguration(
				log.ansiCodesSupported,
				parallelSafe,
				hideSuccessfulOutput,
				!usePipedProcessOutput)

			os.writeObject(config)
		}

		private def writeTestFrameworks(os: ObjectOutputStream) {
			os.writeInt(runners.size)
			for ((testFramework, mainRunner) <- runners) {
				os.writeObject(testFramework.implClassNames.toArray)
				os.writeObject(mainRunner.args)
				os.writeObject(mainRunner.remoteArgs)
			}
			os.flush()
		}

		@tailrec
		private def writeTestSuite(os: ObjectOutputStream, is: ObjectInputStream) {
			val test = suiteQueue.poll()
			if( test == null ) {
				terminateVm(os, is)
			} else {
				os.writeObject(test)
				react(is)
				writeTestSuite(os,is)
			}
		}

		private def terminateVm(os: ObjectOutputStream, is: ObjectInputStream) {
			log.debug("Terminating VM, sending `Done`")
			os.writeObject(ForkTags.Done)
			os.flush()

			// `Done` will be acknoledged by another `Done`
			react(is)
		}

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

		/** read messages sent by the forked VM until `Done` is received. */
		private def react(is: ObjectInputStream) {

			import ForkTags._

			@tailrec
			def react() {
				is.readObject match {
					case `Done` =>

					case Array(`Error`, s: String) => log.error(s); react()
					case Array(`Warn`, s: String) => log.warn(s); react()
					case Array(`Info`, s: String) => log.info(s); react()
					case Array(`Debug`, s: String) => log.debug(s); react()

					case Array(`StartSuite`, name: String) =>
						getOrInitSuite(name)
						listeners.foreach( _.startSuite(name) )
						react()

					case Array(`EndTest`, suiteName: String, stdout: String, event: Event) =>
						val out = if( usePipedProcessOutput ) {
							getPipedProcessOutputAndReset()
						} else {
							stdout
						}
						val shortStdOut = if( out.length > 33 ) out.substring(0, 30) + "[...]" else out
						log.debug("Received EndTest event with stdout: >" + shortStdOut + "<")
						if( usePipedProcessOutput && hideSuccessfulOutput && (event.status() == Status.Failure || event.status() == Status.Error) ) {
							log.error(out)
						}
						val testReport = TestReport(out,event)
						updateSuite(suiteName, _.addTest(testReport))
						listeners.foreach(_.endTest(testReport))
						react()

					case Array(`EndSuite`, name: String) =>
						val suite = getOrInitSuite(name)
						listeners.foreach(_.endSuite(name, suite))
						react()

					case Array(`EndSuiteError`, name: String, error: Throwable) =>
						updateSuite(name, _.error(error) )
						val suite = getOrInitSuite(name)
						listeners.foreach(_.endSuite(name, suite))
						react()

					case t: Throwable =>
						log.trace(t)
						react()
				}
			}

			react()
		}
	}

	private def enqueueSuites(frameworks: Map[TestFramework,Framework], opts: ProcessedOptions, oneItemPerSuites: Boolean) : ConcurrentLinkedQueue[ForkSuites] = {
		val queue = new ConcurrentLinkedQueue[ForkSuites]()

		val taskdefs = opts.tests.map(t => new TaskDef(t.name, forkFingerprint(t.fingerprint), t.explicitlySpecified, t.selectors))

		for {
			(_,framework) <- frameworks
			frameworkFingerprint <- framework.fingerprints()
		} {
			val filteredTaskDefs = taskdefs.filter( t => TestFramework.matches(t.fingerprint(), frameworkFingerprint) )
			if( oneItemPerSuites ) {
				for( taskdef <- filteredTaskDefs ) {
					queue.add(new ForkSuites(framework.name(), taskdef))
				}
			} else {
				queue.add(new ForkSuites(framework.name(), filteredTaskDefs.toArray))
			}
		}

		queue
	}

	private[this] def forkFingerprint(f: Fingerprint): Fingerprint with Serializable =
		f match {
			case s: SubclassFingerprint => new ForkMain.SubclassFingerscan(s)
			case a: AnnotatedFingerprint => new ForkMain.AnnotatedFingerscan(a)
			case _ => sys.error("Unknown fingerprint type: " + f.getClass)
		}

	/**
	 * @param forkOptions options used to create the forked VMs - the output strategy will be overridden if noForkedVm is > 1
	 * @param parallel whether tests should run in parallel in the forked VM - this will be overridden and set to false if noForkedVm is > 1
	 * @param hideSuccessfulOutput whether to hide the output of successful tests
	 */
	private[this] def mainTestTask(frameworks: Map[TestFramework,Framework], runners: Map[TestFramework, Runner], opts: ProcessedOptions, classpath: Seq[File],
																 forkOptions: ForkOptions, log: Logger, parallel: Boolean, hideSuccessfulOutput: Boolean, noForkedVm: Int): Task[TestOutput] = {

		/**
		 * Create a forked process that will execute test suites from `suiteQueue` until the queue becomes empty.
		 * @note if the standard output is not captured, it will not be hidden (no matter `hideSuccessfulOutput`)
		 * @throws RuntimeException if the forked runner cannot be created
		 */
		def fork(suiteQueue: ConcurrentLinkedQueue[ForkSuites], usePipedProcessOutput: Boolean) : ForkedTestRunner = {
			val forkedTestRunner = new ForkedTestRunner(runners, classpath, forkOptions, log, opts.testListeners, parallel, hideSuccessfulOutput, usePipedProcessOutput, suiteQueue)
			forkedTestRunner.start()
			forkedTestRunner
		}

		def failedTestOutput(message: String) : TestOutput = {
			TestOutput(
				TestResult.Error,
				Map(message -> SuiteResult.Error),
				Iterable.empty)
		}

		std.TaskExtra.task
		{
			val testListeners = opts.testListeners flatMap {
				case tl: TestsListener => Some(tl)
				case _ => None
			}

			def onTestsCompletion(output: TestOutput) : TestOutput = {
				testListeners.foreach( _.doComplete(output.overall))
				output
			}

			def awaitForkedVms(vms: Seq[ForkedTestRunner]) : TestOutput = {
				for( vm <- vms ) {
					try { vm.join() } catch { case _ : Throwable => }
				}
				val result = try {
					val results = if( !vms.isEmpty ) {
						vms.map( _.results ).reduce( _ ++ _ )
					} else {
						Map.empty[String,SuiteReport]
					}
					 
					TestOutput(
						TestResult.overall(results.values.map(_.result.result)),
						results.mapValues(_.result).toMap,
						Iterable.empty)
				} catch {
					case NonFatal(e) =>
						failedTestOutput(e.getMessage)
				}
				onTestsCompletion(result)
			}

			testListeners.foreach(_.doInit())

			if( noForkedVm > 1 ) {
				log.debug(s"Creating $noForkedVm forked test runners")
				val suiteQueue = enqueueSuites(frameworks, opts, oneItemPerSuites = true)
				var forkedVms : List[ForkedTestRunner] = Nil
				var noVm = 0
				try {
					while( noVm < noForkedVm ) {
						// when we have multiple VMs we don't want the standard output to be captured within the process
						// and sent over the socket, rather we'll get it through the piped standard output of the process
						forkedVms ::= fork(suiteQueue, usePipedProcessOutput = true)
						noVm += 1
					}
					awaitForkedVms(forkedVms)
				}	catch {
					case err : Throwable =>
						if( noVm == 0 ) {
							onTestsCompletion(failedTestOutput(err.getMessage))
						} else {
							awaitForkedVms(forkedVms)
						}
				}
			} else {
				val suiteQueue = enqueueSuites(frameworks, opts, oneItemPerSuites = false)
				try {
					awaitForkedVms(Seq(fork(suiteQueue, usePipedProcessOutput = false)))
				} catch {
					case err : Throwable => onTestsCompletion(failedTestOutput(err.getMessage))
				}
			}
		}
	}
}