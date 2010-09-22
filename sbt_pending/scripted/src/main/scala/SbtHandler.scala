/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */
package sbt.test

import java.io.{File, IOException}
import xsbt.IPC
import xsbt.test.{StatementHandler, TestFailed}

final class SbtHandler(directory: File, log: Logger, server: IPC.Server) extends StatementHandler
{
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
		val launcherJar = FileUtilities.classLocationFile(Class.forName("xsbti.AppProvider")).getAbsolutePath
		val args = "java" :: "-jar" :: launcherJar :: ( "<" + server.port) :: Nil
		val builder = new java.lang.ProcessBuilder(args.toArray : _*).directory(directory)
		val io = BasicIO(log, false).withInput(_.close())
		val p = Process(builder) run( io )
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
