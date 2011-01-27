package sbt
package compile

import scala.reflect.Manifest
import scala.tools.nsc.{ast, interpreter, io, reporters, util, CompilerCommand, Global, Phase, Settings}
import interpreter.AbstractFileClassLoader
import io.{PlainFile, VirtualDirectory}
import ast.parser.Tokens
import reporters.{ConsoleReporter, Reporter}
import util.BatchSourceFile
import Tokens.EOF
import java.io.File

// TODO: if backing is Some, only recompile if out of date

final class EvalImports(val strings: Seq[(String,Int)], val srcName: String)
final class EvalResult(val tpe: String, val value: Any, val generated: Seq[File], val enclosingModule: String)
final class EvalException(msg: String) extends RuntimeException(msg)
// not thread safe, since it reuses a Global instance
final class Eval(options: Seq[String], mkReporter: Settings => Reporter, parent: ClassLoader)
{
	def this(mkReporter: Settings => Reporter) = this("-cp" :: IO.classLocationFile[ScalaObject].getAbsolutePath :: Nil, mkReporter, getClass.getClassLoader)
	def this() = this(s => new ConsoleReporter(s))

	val settings = new Settings(Console.println)
	val command = new CompilerCommand(options.toList, settings)
	val reporter = mkReporter(settings)
	val global: Global = new Global(settings, reporter)
	import global._
	import definitions._

	def eval(expression: String, imports: EvalImports = noImports, tpeName: Option[String] = None, backing: Option[File] = None, srcName: String = "<setting>", line: Int = DefaultStartLine): EvalResult =
	{
		val moduleName = makeModuleName(srcName, line)
		reporter.reset
		val unit = mkUnit(srcName, line, expression)
		val run = new Run {
			override def units = (unit :: Nil).iterator
		}
		def unlinkAll(): Unit = for( (sym, _) <- run.symSource ) unlink(sym)
		def unlink(sym: Symbol) = sym.owner.info.decls.unlink(sym)

		try
		{
			val (tpe, value) = eval0(expression, imports, tpeName, run, unit, backing, moduleName)
			val classFiles = getClassFiles(backing, moduleName)
			new EvalResult(tpe, value, classFiles, moduleName)
		}
		finally { unlinkAll() }
	}
	def eval0(expression: String, imports: EvalImports, tpeName: Option[String], run: Run, unit: CompilationUnit, backing: Option[File], moduleName: String): (String, Any) =
	{
		val dir = backing match { case None => new VirtualDirectory("<virtual>", None); case Some(dir) => new PlainFile(dir) }
		settings.outputDirs setSingleOutput dir

		val importTrees = parseImports(imports)
		val (parser, tree) = parseExpr(unit)

		val tpt: Tree = tpeName match {
			case Some(tpe) => parseType(tpe)
			case None => TypeTree(NoType)
		}

		unit.body = augment(parser, importTrees, tree, tpt, moduleName)
		
		def compile(phase: Phase): Unit =
		{
			globalPhase = phase
			if(phase == null || phase == phase.next || reporter.hasErrors)
				()
			else
			{
				reporter.withSource(unit.source) {
					atPhase(phase) { phase.run }
				}
				compile(phase.next)
			}
		}

		compile(run.namerPhase)
		checkError("Type error.")
		val tpe = atPhase(run.typerPhase.next) { (new TypeExtractor).getType(unit.body) }

		val loader = new AbstractFileClassLoader(dir, parent)
		(tpe, getValue(moduleName, loader))
	}

	val WrapValName = "$sbtdef"
		//wrap tree in object objectName { def WrapValName = <tree> }
	def augment(parser: global.syntaxAnalyzer.UnitParser, imports: Seq[Tree], tree: Tree, tpt: Tree, objectName: String): Tree =
	{
		def emptyPkg = parser.atPos(0, 0, 0) { Ident(nme.EMPTY_PACKAGE_NAME) }
		def emptyInit = DefDef(
			NoMods,
			nme.CONSTRUCTOR,
			Nil,
			List(Nil),
			TypeTree(),
			Block(List(Apply(Select(Super("", ""), nme.CONSTRUCTOR), Nil)), Literal(Constant(())))
		)

		def method = DefDef(NoMods, WrapValName, Nil, Nil, tpt, tree)
		def moduleBody = Template(List(gen.scalaScalaObjectConstr), emptyValDef, List(emptyInit, method))
		def moduleDef = ModuleDef(NoMods, objectName, moduleBody)
		parser.makePackaging(0, emptyPkg, (imports :+ moduleDef).toList)
	}

	def getValue[T](objectName: String, loader: ClassLoader): T =
	{
		val clazz = Class.forName(objectName + "$", true, loader)
		val module = clazz.getField("MODULE$").get(null)
		val accessor = module.getClass.getMethod(WrapValName)
		val value = accessor.invoke(module)
		value.asInstanceOf[T]
	}

	final class TypeExtractor extends Traverser {
		private[this] var result = ""
		def getType(t: Tree) = { result = ""; traverse(t); result }
		override def traverse(tree: Tree): Unit = tree match {
			case d: DefDef if  d.symbol.nameString == WrapValName => result = d.symbol.tpe.finalResultType.toString
			case _ => super.traverse(tree)
		}
	}
	// TODO: use the code from Analyzer
	private[this] def getClassFiles(backing: Option[File], moduleName: String): Seq[File] =
		backing match {
			case None => Nil
			case Some(dir) => dir listFiles moduleClassFilter(moduleName)
		}
	private[this] def moduleClassFilter(moduleName: String) = new java.io.FilenameFilter { def accept(dir: File, s: String) =
		(s contains moduleName) && (s endsWith ".class")
	}
	private[this] def parseExpr(unit: CompilationUnit) = 
	{
		val parser = new syntaxAnalyzer.UnitParser(unit)
		val tree: Tree = parser.expr()
		parser.accept(EOF)
		checkError("Error parsing expression.")
		(parser, tree)
	}
	private[this] def parseType(tpe: String): Tree =
	{
		val tpeParser = new syntaxAnalyzer.UnitParser(mkUnit("<expected-type>", DefaultStartLine, tpe))
		val tpt0: Tree = tpeParser.typ()
		tpeParser.accept(EOF)
		checkError("Error parsing type.")
		tpt0
	}
	private[this] def parseImports(imports: EvalImports): Seq[Tree] =
		imports.strings flatMap { case (s, line) => parseImport(mkUnit(imports.srcName, line, s)) }
	private[this] def parseImport(importUnit: CompilationUnit): Seq[Tree] =
	{
		val parser = new syntaxAnalyzer.UnitParser(importUnit)
		val trees: Seq[Tree] = parser.importClause()
		parser.accept(EOF)
		checkError("Error parsing imports.")
		trees
	}


	val DefaultStartLine = 0
	private[this] def makeModuleName(src: String, line: Int): String  =  "$" + halve(Hash.toHex(Hash(src + ":" + line)))
	private[this] def halve(s: String) = if(s.length > 2) s.substring(0, s.length / 2)
	private[this] def noImports = new EvalImports(Nil, "")
	private[this] def mkUnit(srcName: String, firstLine: Int, s: String) = new CompilationUnit(new EvalSourceFile(srcName, firstLine, s))
	private[this] def checkError(label: String) = if(reporter.hasErrors) throw new EvalException(label)

	private[this] final class EvalSourceFile(name: String, startLine: Int, contents: String) extends BatchSourceFile(name, contents)
	{
		override def lineToOffset(line: Int): Int = super.lineToOffset((line - startLine) max 0)
		override def offsetToLine(offset: Int): Int = super.offsetToLine(offset) + startLine
	}
}
