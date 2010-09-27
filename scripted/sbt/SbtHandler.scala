/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */
package sbt
package test

	import java.io.{File, IOException}
	import xsbt.IPC
	import xsbt.test.{StatementHandler, TestFailed}

	import Logger._

final class SbtHandler(directory: File, launcher: File, log: Logger, server: IPC.Server) extends StatementHandler
{
	def this(directory: File, launcher: File, log: xsbti.Logger, server: IPC.Server) = this(directory, launcher, log: Logger, server)
	type State = Process
	def initialState = newRemote
	def apply(command: String, arguments: List[String], p: Process): Process =
	{
		send((command :: arguments.map(escape)).mkString(" "))
		receive(command + " failed")
		p
	}
	def finish(state: Process) =
		try {
			server.connection { _.send("exit") }
			state.exitValue()
		} catch {
			case e: IOException =>  state.destroy()
		}
	def send(message: String) = server.connection { _.send(message) }
	def receive(errorMessage: String) =
		server.connection { ipc =>
			val resultMessage = ipc.receive
			if(!resultMessage.toBoolean) throw new TestFailed(errorMessage)
		}
	def newRemote =
	{
		val launcherJar = launcher.getAbsolutePath
		val args = "java" :: "-jar" :: launcherJar :: "loadp" :: ( "<" + server.port) :: Nil
		val io = BasicIO(log, false).withInput(_.close())
		val p = Process(args, directory) run( io )
		Spawn { p.exitValue(); server.close() }
		try { receive("Remote sbt initialization failed") }
		catch { case e: java.net.SocketException => error("Remote sbt initialization failed") }
		p
	}
	import java.util.regex.Pattern.{quote => q}
	// if the argument contains spaces, enclose it in quotes, quoting backslashes and quotes
	def escape(argument: String) =
		if(argument.contains(" ")) "\"" + argument.replaceAll(q("""\"""), """\\""").replaceAll(q("\""), "\\\"") + "\"" else argument
}
