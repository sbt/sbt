/* sbt -- Simple Build Tool
 * Copyright 2011  Mark Harrah
 */
package sbt

	import java.io.File
	import Keys._
	import EvaluateConfigurations.{evaluateConfiguration => evaluate}
	import Configurations.Compile

object Script
{
	final val Name = "script"
	lazy val command = 
		Command.command(Name) { state =>
			val scriptArg = state.remainingCommands.headOption getOrElse error("No script file specified")
			val script = new File(scriptArg).getAbsoluteFile
			val hash = halve(Hash.toHex(Hash(script.getAbsolutePath)))
			val base = new File(state.configuration.provider.scalaProvider.launcher.bootDirectory, hash)
			IO.createDirectory(base)

			val (eval, structure) = Load.defaultLoad(state, base, CommandSupport.logger(state))
			val session = Load.initialSession(structure, eval)
			val extracted = Project.extract(session, structure)
				import extracted._

			val embeddedSettings = blocks(script).flatMap { block =>
				evaluate(eval(), script.getPath, block.lines, currentUnit.imports, block.offset+1)
			}
			val scriptAsSource = sources in Compile := script :: Nil
			val asScript = scalacOptions ++= Seq("-Xscript", script.getName.stripSuffix(".scala"))
			val scriptSettings = Seq(asScript, scriptAsSource, logLevel in Global := Level.Warn, showSuccess in Global := false)
			val append = Load.transformSettings(Load.projectScope(currentRef), currentRef.build, rootProject, scriptSettings ++ embeddedSettings)
                   
			val newStructure = Load.reapply(session.original ++ append, structure)
			val newState = "run" :: state.copy(remainingCommands = state.remainingCommands.drop(1))
			Project.setProject(session, newStructure, newState)
		}
	def halve(s: String): String = if(s.length > 3) s.substring(0, s.length / 2) else s

	final case class Block(offset: Int, lines: Seq[String])
	def blocks(file: File): Seq[Block] =
	{
		val lines = IO.readLines(file).toIndexedSeq
		def blocks(b: Block, acc: List[Block]): List[Block] =
			if(b.lines.isEmpty)
				acc.reverse
			else
			{
				val (dropped, blockToEnd) = b.lines.span { line => ! line.startsWith(BlockStart) }
				val (block, remaining) = blockToEnd.span { line => ! line.startsWith(BlockEnd) }
				val offset = b.offset + dropped.length
				blocks(Block(offset + block.length, remaining), Block(offset, block.drop(1)) :: acc )
			}
		blocks(Block(0, lines), Nil)
	}
	val BlockStart = "/***"
	val BlockEnd = "*/"
	def fail(s: State, msg: String): State =
	{
		System.err.println(msg)
		s.fail
	}
}
