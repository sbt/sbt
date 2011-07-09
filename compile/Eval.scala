package sbt
package compiler

import scala.reflect.Manifest
import scala.tools.nsc.{ast, interpreter, io, reporters, util, CompilerCommand, Global, Phase, Settings}
import interpreter.AbstractFileClassLoader
import io.{AbstractFile, PlainFile, VirtualDirectory}
import ast.parser.Tokens
import reporters.{ConsoleReporter, Reporter}
import util.BatchSourceFile
import Tokens.{EOF, NEWLINE, NEWLINES, SEMI}
import java.io.File
import java.nio.ByteBuffer
import java.net.URLClassLoader

// TODO: provide a way to cleanup backing directory

final class EvalImports(val strings: Seq[(String,Int)], val srcName: String)
final class EvalResult(val tpe: String, val getValue: ClassLoader => Any, val generated: Seq[File], val enclosingModule: String)
final class EvalException(msg: String) extends RuntimeException(msg)
// not thread safe, since it reuses a Global instance
final class Eval(optionsNoncp: Seq[String], classpath: Seq[File], mkReporter: Settings => Reporter, backing: Option[File])
{
	def this(mkReporter: Settings => Reporter, backing: Option[File]) = this(Nil, IO.classLocationFile[ScalaObject] :: Nil, mkReporter, backing)
	def this() = this(s => new ConsoleReporter(s), None)

	backing.foreach(IO.createDirectory)
	val classpathString = Path.makeString(classpath)
	val options = "-cp" +: classpathString +: optionsNoncp

	lazy val settings =
	{
		val s = new Settings(println)
		val command = new CompilerCommand(options.toList, s)
		s
	}
	lazy val reporter = mkReporter(settings)
	lazy val global: Global = new Global(settings, reporter)
	import global._
	import definitions._

	def eval(expression: String, imports: EvalImports = noImports, tpeName: Option[String] = None, srcName: String = "<setting>", line: Int = DefaultStartLine): EvalResult =
	{
			import Eval._
		val hash = Hash.toHex(Hash(bytes( bytes(expression) :: optBytes(backing)(fileExistsBytes) :: seqBytes(options)(bytes) ::
			seqBytes(classpath)(fileModifiedBytes) :: seqBytes(imports.strings.map(_._1))(bytes) :: optBytes(tpeName)(bytes) :: Nil)))
		val moduleName = makeModuleName(hash)
		
		lazy val unit = {
			reporter.reset
			mkUnit(srcName, line, expression)
		}
		lazy val run = new Run {
			override def units = (unit :: Nil).iterator
		}
		def unlinkAll(): Unit = for( (sym, _) <- run.symSource ) unlink(sym)
		def unlink(sym: Symbol) = sym.owner.info.decls.unlink(sym)

		val (tpe, value) =
			(tpeName, backing) match {
				case (Some(tpe), Some(back)) if classExists(back, moduleName) => (tpe, loadPlain(back, moduleName))
				case _ => try { eval0(expression, imports, tpeName, run, unit, backing, moduleName) } finally { unlinkAll() }
			}
		val classFiles = getClassFiles(backing, moduleName)
		new EvalResult(tpe, value, classFiles, moduleName)
	}
	def eval0(expression: String, imports: EvalImports, tpeName: Option[String], run: Run, unit: CompilationUnit, backing: Option[File], moduleName: String): (String, ClassLoader => Any) =
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
		checkError("Type error in expression")
		val tpe = atPhase(run.typerPhase.next) { (new TypeExtractor).getType(unit.body) }

		(tpe, load(dir, moduleName))
	}
	def load(dir: AbstractFile, moduleName: String): ClassLoader => Any  = parent => getValue[Any](moduleName, new AbstractFileClassLoader(dir, parent))
	def loadPlain(dir: File, moduleName: String): ClassLoader => Any  = parent => getValue[Any](moduleName, new URLClassLoader(Array(dir.toURI.toURL), parent))

	val WrapValName = "$sbtdef"
		//wrap tree in object objectName { def WrapValName = <tree> }
	def augment(parser: global.syntaxAnalyzer.UnitParser, imports: Seq[Tree], tree: Tree, tpt: Tree, objectName: String): Tree =
	{
		val emptyTypeName = nme.EMPTY.toTypeName
		def emptyPkg = parser.atPos(0, 0, 0) { Ident(nme.EMPTY_PACKAGE_NAME) }
		def emptyInit = DefDef(
			NoMods,
			nme.CONSTRUCTOR,
			Nil,
			List(Nil),
			TypeTree(),
			Block(List(Apply(Select(Super(emptyTypeName, emptyTypeName), nme.CONSTRUCTOR), Nil)), Literal(Constant(())))
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
	private[this] def classExists(dir: File, name: String) = (new File(dir, name + ".class")).exists
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
		val extra = parser.in.token match {
			case EOF => "  Ensure that there are no blank lines within a setting."
			case _ => ""
		}
		checkError("Error parsing expression." + extra)

		parser.accept(EOF)
		val extra2 = parser.in.token match {
			case SEMI => "  Note that settings are expressions and do not end with semicolons.  (Semicolons are fine within {} blocks, however.)"
			case NEWLINE | NEWLINES => "  Ensure that settings are separated by blank lines."
			case _ => ""
		}
		checkError("Error parsing expression." + extra2)

		(parser, tree)
	}
	private[this] def parseType(tpe: String): Tree =
	{
		val tpeParser = new syntaxAnalyzer.UnitParser(mkUnit("<expected-type>", DefaultStartLine, tpe))
		val tpt0: Tree = tpeParser.typ()
		tpeParser.accept(EOF)
		checkError("Error parsing expression type.")
		tpt0
	}
	private[this] def parseImports(imports: EvalImports): Seq[Tree] =
		imports.strings flatMap { case (s, line) => parseImport(mkUnit(imports.srcName, line, s)) }
	private[this] def parseImport(importUnit: CompilationUnit): Seq[Tree] =
	{
		val parser = new syntaxAnalyzer.UnitParser(importUnit)
		val trees: Seq[Tree] = parser.importClause()
		parser.accept(EOF)
		checkError("Error parsing imports for expression.")
		trees
	}


	val DefaultStartLine = 0
	private[this] def makeModuleName(hash: String): String  =  "$" + Hash.halve(hash)
	private[this] def noImports = new EvalImports(Nil, "")
	private[this] def mkUnit(srcName: String, firstLine: Int, s: String) = new CompilationUnit(new EvalSourceFile(srcName, firstLine, s))
	private[this] def checkError(label: String) = if(reporter.hasErrors) throw new EvalException(label)

	private[this] final class EvalSourceFile(name: String, startLine: Int, contents: String) extends BatchSourceFile(name, contents)
	{
		override def lineToOffset(line: Int): Int = super.lineToOffset((line - startLine) max 0)
		override def offsetToLine(offset: Int): Int = super.offsetToLine(offset) + startLine
	}
}
private object Eval
{
	def optBytes[T](o: Option[T])(f: T => Array[Byte]): Array[Byte] = seqBytes(o.toSeq)(f)
	def seqBytes[T](s: Seq[T])(f: T => Array[Byte]): Array[Byte] = bytes(s map f)
	def bytes(b: Seq[Array[Byte]]): Array[Byte] = bytes(b.length) ++ b.flatten.toArray[Byte]
	def bytes(b: Boolean): Array[Byte] = Array[Byte](if(b) 1 else 0)
	def filesModifiedBytes(fs: Array[File]): Array[Byte] = if(fs eq null) filesModifiedBytes(Array[File]()) else seqBytes(fs)(fileModifiedBytes)
	def fileModifiedBytes(f: File): Array[Byte] =
		(if(f.isDirectory) filesModifiedBytes(f listFiles classDirFilter) else bytes(f.lastModified)) ++
		bytes(f.getAbsolutePath)
	def fileExistsBytes(f: File): Array[Byte] =
		bytes(f.exists) ++
		bytes(f.getAbsolutePath)
 
	def bytes(s: String): Array[Byte] = s getBytes "UTF-8"
	def bytes(l: Long): Array[Byte] =
	{
		val buffer = ByteBuffer.allocate(8)
		buffer.putLong(l)
		buffer.array
	}
	def bytes(i: Int): Array[Byte] =
	{
		val buffer = ByteBuffer.allocate(4)
		buffer.putInt(i)
		buffer.array
	}

	private val classDirFilter: FileFilter = DirectoryFilter || GlobFilter("*.class")
}