/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */
package xsbt

import scala.tools.nsc.{io, symtab, Phase}
import io.{AbstractFile, PlainFile, ZipArchive}
import symtab.Flags

import java.io.File

object Dependency
{
	def name = "xsbt-dependency"
}
/**
 * Extracts dependency information from each compilation unit.
 *
 * This phase uses CompilationUnit.depends and CallbackGlobal.inheritedDependencies
 * to collect all symbols that given compilation unit depends on. Those symbols are
 * guaranteed to represent Class-like structures.
 *
 * The CallbackGlobal.inheritedDependencies is populated by the API phase. See,
 * ExtractAPI class.
 *
 * When dependency symbol is processed, it is mapped back to either source file where
 * it's defined in (if it's available in current compilation run) or classpath entry
 * where it originates from. The Symbol->Classfile mapping is implemented by
 * LocateClassFile that we inherit from.
 */
final class Dependency(val global: CallbackGlobal) extends LocateClassFile
{
	import global._

	def newPhase(prev: Phase): Phase = new DependencyPhase(prev)
	private class DependencyPhase(prev: Phase) extends Phase(prev)
	{
		override def description = "Extracts dependency information"
		def name = Dependency.name
		def run
		{
			for(unit <- currentRun.units if !unit.isJava)
			{
				// build dependencies structure
				val sourceFile = unit.source.file.file
				if (global.callback.memberRefAndInheritanceDeps) {
					val dependenciesByMemberRef = extractDependenciesByMemberRef(unit)
					for(on <- dependenciesByMemberRef)
						processDependency(on, inherited=false)

					val dependenciesByInheritance = extractDependenciesByInheritance(unit)
					for(on <- dependenciesByInheritance)
						processDependency(on, inherited=true)
				} else {
					for(on <- unit.depends) processDependency(on, inherited=false)
					for(on <- inheritedDependencies.getOrElse(sourceFile, Nil: Iterable[Symbol])) processDependency(on, inherited=true)
				}
				/**
				 * Handles dependency on given symbol by trying to figure out if represents a term
				 * that is coming from either source code (not necessarily compiled in this compilation
				 * run) or from class file and calls respective callback method.
				 */
				def processDependency(on: Symbol, inherited: Boolean)
				{
					def binaryDependency(file: File, className: String) = callback.binaryDependency(file, className, sourceFile, inherited)
					val onSource = on.sourceFile
					if(onSource == null)
					{
						classFile(on) match
						{
							case Some((f,className,inOutDir)) =>
								if(inOutDir && on.isJavaDefined) registerTopLevelSym(on)
								f match
								{
									case ze: ZipArchive#Entry => for(zip <- ze.underlyingSource; zipFile <- Option(zip.file) ) binaryDependency(zipFile, className)
									case pf: PlainFile => binaryDependency(pf.file, className)
									case _ => ()
								}
							case None => ()
						}
					}
					else if (onSource.file != sourceFile)
						callback.sourceDependency(onSource.file, sourceFile, inherited)
				}
			}
		}
	}

	/**
	 * Traverses given type and collects result of applying a partial function `pf`.
	 *
	 * NOTE: This class exists in Scala 2.10 as CollectTypeCollector but does not in earlier
	 * versions (like 2.9) of Scala compiler that incremental cmpiler supports so we had to
	 * reimplement that class here.
	 */
	private final class CollectTypeTraverser[T](pf: PartialFunction[Type, T]) extends TypeTraverser {
		var collected: List[T] = Nil
		def traverse(tpe: Type): Unit = {
			if (pf.isDefinedAt(tpe))
				collected = pf(tpe) :: collected
			mapOver(tpe)
		}
	}

	private abstract class ExtractDependenciesTraverser extends Traverser {
		protected val depBuf = collection.mutable.ArrayBuffer.empty[Symbol]
		protected def addDependency(dep: Symbol): Unit = depBuf += dep
		def dependencies: collection.immutable.Set[Symbol] = {
			// convert to immutable set and remove NoSymbol if we have one
			depBuf.toSet - NoSymbol
		}
	}

	private class ExtractDependenciesByMemberRefTraverser extends ExtractDependenciesTraverser {
		override def traverse(tree: Tree): Unit = {
			tree match {
				case Import(expr, selectors) =>
					selectors.foreach {
						case ImportSelector(nme.WILDCARD, _, null, _) =>
						// in case of wildcard import we do not rely on any particular name being defined
						// on `expr`; all symbols that are being used will get caught through selections
						case ImportSelector(name: Name, _, _, _) =>
							def lookupImported(name: Name) = expr.symbol.info.member(name)
							// importing a name means importing both a term and a type (if they exist)
							addDependency(lookupImported(name.toTermName))
							addDependency(lookupImported(name.toTypeName))
					}
				case select: Select =>
					addDependency(select.symbol)
				/*
				 * Idents are used in number of situations:
				 *  - to refer to local variable
				 *  - to refer to a top-level package (other packages are nested selections)
				 *  - to refer to a term defined in the same package as an enclosing class;
				 *    this looks fishy, see this thread:
				 *    https://groups.google.com/d/topic/scala-internals/Ms9WUAtokLo/discussion
				 */
				case ident: Ident =>
				    addDependency(ident.symbol)
				case typeTree: TypeTree =>
					val typeSymbolCollector = new CollectTypeTraverser({
						case tpe if !tpe.typeSymbol.isPackage => tpe.typeSymbol
					})
					typeSymbolCollector.traverse(typeTree.tpe)
					val deps = typeSymbolCollector.collected.toSet
					deps.foreach(addDependency)
				case Template(parents, self, body) =>
					traverseTrees(body)
				case other => ()
			}
			super.traverse(tree)
		}
	}

	private def extractDependenciesByMemberRef(unit: CompilationUnit): collection.immutable.Set[Symbol] = {
		val traverser = new ExtractDependenciesByMemberRefTraverser
		traverser.traverse(unit.body)
		val dependencies = traverser.dependencies
		// we capture enclosing classes only because that's what CompilationUnit.depends does and we don't want
		// to deviate from old behaviour too much for now
		dependencies.map(_.toplevelClass)
	}

	/** Copied straight from Scala 2.10 as it does not exist in Scala 2.9 compiler */
	private final def debuglog(msg: => String) {
		if (settings.debug.value)
			log(msg)
	}

	private final class ExtractDependenciesByInheritanceTraverser extends ExtractDependenciesTraverser {
		override def traverse(tree: Tree): Unit = tree match {
			case Template(parents, self, body) =>
				// we are using typeSymbol and not typeSymbolDirect because we want
				// type aliases to be expanded
				val parentTypeSymbols = parents.map(parent => parent.tpe.typeSymbol).toSet
				debuglog("Parent type symbols for " + tree.pos + ": " + parentTypeSymbols.map(_.fullName))
				parentTypeSymbols.foreach(addDependency)
				traverseTrees(body)
			case tree => super.traverse(tree)
		}
	}

	private def extractDependenciesByInheritance(unit: CompilationUnit): collection.immutable.Set[Symbol] = {
		val traverser = new ExtractDependenciesByInheritanceTraverser
		traverser.traverse(unit.body)
		val dependencies = traverser.dependencies
		dependencies.map(_.toplevelClass)
	}

}
