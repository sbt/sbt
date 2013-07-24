/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010, 2011 Mark Harrah
 */
package xsbt

import java.io.File
import java.util.{Arrays,Comparator}
import scala.tools.nsc.{io, plugins, symtab, Global, Phase}
import io.{AbstractFile, PlainFile, ZipArchive}
import plugins.{Plugin, PluginComponent}
import symtab.Flags
import scala.collection.mutable.{HashMap, HashSet, ListBuffer}
import xsbti.api.{ClassLike, DefinitionType, PathComponent, SimpleType}

object API
{
	val name = "xsbt-api"
}

final class API(val global: CallbackGlobal) extends Compat
{
	import global._

	@inline def debug(msg: => String) = if(settings.verbose.value) inform(msg)

	def newPhase(prev: Phase) = new ApiPhase(prev)
	class ApiPhase(prev: Phase) extends Phase(prev)
	{
		override def description = "Extracts the public API from source files."
		def name = API.name
		def run: Unit =
		{
			val start = System.currentTimeMillis
			currentRun.units.foreach(processUnit)
			val stop = System.currentTimeMillis
			debug("API phase took : " + ((stop - start)/1000.0) + " s")
		}
		def processUnit(unit: CompilationUnit) = if(!unit.isJava) processScalaUnit(unit)
		def processScalaUnit(unit: CompilationUnit)
		{
			val sourceFile = unit.source.file.file
			debug("Traversing " + sourceFile)
			val extractApi = new ExtractAPI[global.type](global, sourceFile)
			val traverser = new TopLevelHandler(extractApi)
			traverser.apply(unit.body)
			val packages = traverser.packages.toArray[String].map(p => new xsbti.api.Package(p))
			val source = new xsbti.api.SourceAPI(packages, traverser.definitions.toArray[xsbti.api.Definition])
			extractApi.forceStructures()
			callback.api(sourceFile, source)
		}
	}


	private final class TopLevelHandler(extractApi: ExtractAPI[global.type]) extends TopLevelTraverser
	{
		val packages = new HashSet[String]
		val definitions = new ListBuffer[xsbti.api.Definition]
		def `class`(c: Symbol): Unit = {
			definitions += extractApi.classLike(c.owner, c)
		}
		/** Record packages declared in the source file*/
		def `package`(p: Symbol)
		{
			if( (p eq null) || p == NoSymbol || p.isRoot || p.isRootPackage || p.isEmptyPackageClass || p.isEmptyPackage)
				()
			else
			{
				packages += p.fullName
				`package`(p.enclosingPackage)
			}
		}
	}

	private abstract class TopLevelTraverser extends Traverser
	{
		def `class`(s: Symbol)
		def `package`(s: Symbol)
		override def traverse(tree: Tree)
		{
			tree match
			{
				case (_: ClassDef | _ : ModuleDef) if isTopLevel(tree.symbol) => `class`(tree.symbol)
				case p: PackageDef =>
					`package`(p.symbol)
					super.traverse(tree)
				case _ =>
			}
		}
		def isTopLevel(sym: Symbol): Boolean =
			(sym ne null) && (sym != NoSymbol) && !sym.isImplClass && !sym.isNestedClass && sym.isStatic &&
			!sym.hasFlag(Flags.SYNTHETIC) && !sym.hasFlag(Flags.JAVA)
	}



}
