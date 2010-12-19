package sbt
package compile

import scala.reflect.Manifest
import scala.tools.nsc.{ast, interpreter, io, reporters, util, CompilerCommand, Global, Phase, Settings}
import interpreter.AbstractFileClassLoader
import io.VirtualDirectory
import ast.parser.Tokens
import reporters.{ConsoleReporter, Reporter}
import util.BatchSourceFile
import Tokens.EOF

final class EvalException(msg: String) extends RuntimeException(msg)
// not thread safe, since it reuses a Global instance
final class Eval(options: Seq[String], mkReporter: Settings => Reporter, parent: ClassLoader)
{
	def this() = this("-cp" :: IO.classLocationFile[ScalaObject].getAbsolutePath :: Nil, s => new ConsoleReporter(s), getClass.getClassLoader)

	val settings = new Settings(Console.println)
	val command = new CompilerCommand(options.toList, settings)
	val reporter = mkReporter(settings)
	val global: Global = new Global(settings, reporter)
	import global._
	import definitions._

	def eval[T](expression: String)(implicit mf: Manifest[T]): T = eval(expression, Some(mf.toString)).asInstanceOf[T]
	def eval(expression: String, tpeName: Option[String]): Any =
	{
		reporter.reset
		val unit = mkUnit(expression)
		val run = new Run {
			override def units = (unit :: Nil).iterator
		}
		def unlinkAll(): Unit = for( (sym, _) <- run.symSource ) unlink(sym)
		def unlink(sym: Symbol) = sym.owner.info.decls.unlink(sym)

		try { eval0(expression, tpeName, run, unit) } finally { unlinkAll() }
	}
	def eval0(expression: String, tpeName: Option[String], run: Run, unit: CompilationUnit): (String, Any) =
	{
		val virtualDirectory = new VirtualDirectory("<virtual>", None)
		settings.outputDirs setSingleOutput virtualDirectory

		val parser = new syntaxAnalyzer.UnitParser(unit)
		val tree: Tree = parser.expr()
		parser.accept(EOF)
		checkError("Error parsing expression.")

		val tpt: Tree = tpeName match {
			case Some(tpe) =>
				val tpeParser = new syntaxAnalyzer.UnitParser(mkUnit(tpe))
				val tpt0: Tree = tpeParser.typ()
				tpeParser.accept(EOF)
				checkError("Error parsing type.")
				tpt0
			case None => TypeTree(NoType)
		}

		unit.body = augment(parser, tree, tpt)
		
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

		val loader = new AbstractFileClassLoader(virtualDirectory, parent)
		(tpe, getValue(loader))
	}
	val WrapObjectName = "$sbtobj"
	val WrapValName = "$sbtdef"
		//wrap tree in object WrapObjectName { def WrapValName = <tree> }
	def augment(parser: global.syntaxAnalyzer.UnitParser, tree: Tree, tpt: Tree): Tree =
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
		def moduleDef = ModuleDef(NoMods, WrapObjectName, moduleBody)
		parser.makePackaging(0, emptyPkg, List(moduleDef))
	}

	def getValue[T](loader: ClassLoader): T =
	{
		val clazz = Class.forName(WrapObjectName + "$", true, loader)
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

	def mkUnit(s: String) = new CompilationUnit(new BatchSourceFile("<setting>", s))
	def checkError(label: String) = if(reporter.hasErrors) throw new EvalException(label)
}
