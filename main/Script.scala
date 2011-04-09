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
	lazy val command = 
		Command.command("script") { state =>
			val scriptArg = state.remainingCommands.headOption getOrElse error("No script file specified")
			val script = new File(scriptArg).getAbsoluteFile
			val extracted = Project.extract(state)
				import extracted._

			val eval = session.currentEval()
			val settings = blocks(script).flatMap { block =>
				evaluate(eval, script.getPath, block.lines, currentUnit.imports, block.offset)
			}
			val scriptAsSource = sources in Compile := script :: Nil
			val asScript = scalacOptions ++= Seq("-Xscript", script.getName.stripSuffix(".scala"))
			val logQuiet = (logLevel in Global := Level.Warn) :: (showSuccess in Global := false) :: Nil
			val append = Load.transformSettings(Load.projectScope(currentRef), currentRef.build, rootProject, asScript +: (logQuiet ++ settings))
			val newStructure = Load.reapply(session.original ++ append, structure)
			val newState = "run" :: state.copy(remainingCommands = state.remainingCommands.drop(1))
			Project.setProject(session, newStructure, newState)
		}

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
