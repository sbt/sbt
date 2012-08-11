/* sbt -- Simple Build Tool
 * Copyright 2012 Eugene Vigdorchik
 */
package sbt

import org.scalatools.testing._
import java.net.ServerSocket
import java.io._
import Tests._
import ForkMain._

private[sbt] object ForkTests {
	def apply(frameworks: Seq[TestFramework], tests: List[TestDefinition], config: Execution, classpath: Seq[File], javaHome: Option[File], javaOpts: Seq[String], log: Logger): Task[Output]  = {
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
				case Argument(None, args) =>  args
				case Argument(Some(`f`), args) => args
				case _ => Nil
			}
		}.toMap

		std.TaskExtra.task {
			val server = new ServerSocket(0)
			object Acceptor extends Runnable {
				val results = collection.mutable.Map.empty[String, TestResult.Value]
				def output = (overall(results.values), results.toMap)
				def run = {
						val socket = server.accept()
						val os = new ObjectOutputStream(socket.getOutputStream)
						val is = new ObjectInputStream(socket.getInputStream)

						import ForkTags._
						@annotation.tailrec def react: Unit = is.readObject match {
							case `Done` => os.writeObject(Done);
							case Array(`Error`, s: String) => log.error(s); react
							case Array(`Warn`, s: String) => log.warn(s); react
							case Array(`Info`, s: String) => log.info(s); react
							case Array(`Debug`, s: String) => log.debug(s); react
							case t: Throwable => log.trace(t); react
							case Array(group: String, tEvents: Array[Event]) =>
								listeners.foreach(_ startGroup group)
								val event = TestEvent(tEvents)
								listeners.foreach(_ testEvent event)
								val result = event.result getOrElse TestResult.Passed
								results += group -> result
								listeners.foreach(_ endGroup (group, result))
								react
						}

						try {
							os.writeObject(Boolean.box(log.ansiCodesSupported))
							
							val testsFiltered = tests.filter(test => filters.forall(_(test.name))).map{
								t => new ForkTestDefinition(t.name, t.fingerprint)
							}.toArray
							os.writeObject(testsFiltered)

							os.writeObject(Int.box(frameworks.size))
							for ((clazz, args) <- argMap) {
								os.writeObject(clazz)
								os.writeObject(args.toArray)
							}

							react
						} finally {
							is.close();	os.close(); socket.close()
						}
					}
			}

			if (!tests.isEmpty) {
				try {
					testListeners.foreach(_.doInit())
					new Thread(Acceptor).start()

					val fullCp = classpath ++: Seq(IO.classLocationFile[ForkMain], IO.classLocationFile[Framework])
					val options = javaOpts ++: Seq("-classpath", fullCp mkString File.pathSeparator, classOf[ForkMain].getCanonicalName, server.getLocalPort.toString)
					val ec = Fork.java(javaHome, options, StdoutOutput)
					if (ec != 0) log.error("Running java with options " + options.mkString(" ") + " failed with exit code " + ec)
				} finally {
					server.close()
				}
				val result = Acceptor.output
				testListeners.foreach(_.doComplete(result._1))
				result
			} else
				(TestResult.Passed, Map.empty[String, TestResult.Value])
		} tagw (config.tags: _*)
	}
}
