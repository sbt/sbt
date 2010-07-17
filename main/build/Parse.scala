/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt
package build

import java.io.File

final class ParseException(msg: String) extends RuntimeException(msg)

/** Parses a load command.
*
* load ::= 'load' (binary | source | project)
*
* binary ::= classpath module name
* source ::= classpath '-src' paths ('-d' dir)? ('-auto' ('sub' | 'annot'))? module name
* project ::= ('-project' path)? name?
*
* name ::= '-name' nameString
* module ::= ('-module' ('true'|'false') )?
* classpath ::= '-cp' paths 
* path ::= pathChar+
* paths ::= path (pathSep path)*
*/

object Parse
{
	import File.{pathSeparatorChar => sep}

	def error(msg: String) = throw new ParseException(msg)
	def apply(commandString: String)(implicit base: File): LoadCommand =
	{
		val tokens = commandString.split("""\s+""").toSeq
		if(tokens.isEmpty) error("Empty command")
		else if(tokens.head != "load") error("Not a load command")
		else
		{
			val args = tokens.drop(1)
			val srcs = sourcepath(args)
			val nme = name(args)
			
			lazy val cp = classpath(args)
			lazy val mod = module(args)
			lazy val proj = project(args).getOrElse(base)
			
			if(!srcs.isEmpty)
				SourceLoad(cp, srcs, output(args), mod, auto(args), nme)
			else if(!cp.isEmpty)
				BinaryLoad(cp, mod, nme)
			else
				ProjectLoad(proj, nme)
		}
	}
	
	def auto(args: Seq[String]): Auto.Value =
		getArg(args, "auto") match {
			case None => Auto.Explicit
			case Some("sub") => Auto.Subclass
			case Some("annot") => Auto.Annotation
			case Some(x) => error("Illegal auto argument '" + x + "'")
		}
	
	def module(args: Seq[String]): Boolean =
		getArg(args, "module") match {
			case None | Some("false") => false
			case Some("true") => true
			case Some(x) => error("Expected boolean, got '" + x + "'")
		}
		
	def name(args: Seq[String]): String =
		getArg(args, "name") getOrElse("")
	
	def output(args: Seq[String])(implicit base: File): Option[File] =
		getArg(args, "d") map file(base)
		
	def project(args: Seq[String])(implicit base: File): Option[File] =
		getArg(args, "project") map file(base)
	
	def pathArg(args: Seq[String], name: String)(implicit base: File): Seq[File] =
		getArg(args, name).toSeq flatMap paths
	
	def classpath(args: Seq[String])(implicit base: File): Seq[File] = pathArg(args, "cp")
	def sourcepath(args: Seq[String])(implicit base: File): Seq[File] = pathArg(args, "src")
	
	def getArg(args: Seq[String], name: String): Option[String] =
	{
		val opt = "-" + name
		val found = args.dropWhile(_ != opt)
		
		if(found.isEmpty)
			None
		else
			found.drop(1).headOption match
			{
				case x @ Some(arg) if !arg.startsWith("-") => x
				case _ => error("No argument provided for -" + name)
			}
	}

	def paths(implicit base: File): String => Seq[File] =
		_ split sep map file(base)
		
	def file(base: File) = (path: String) => Path.fromString(base, path).asFile
}