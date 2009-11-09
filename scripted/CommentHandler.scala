package xsbt.test

import java.io.File
import xsbt.{FileMapper, FileUtilities, Paths}

object CommentHandler extends BasicStatementHandler
{
	def apply(command: String, args: List[String]) = ()
}