/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */
package sbt

import scala.tools.nsc.{io, plugins, symtab, Global, Phase}
import io.{AbstractFile, PlainFile, ZipArchive}
import plugins.{Plugin, PluginComponent}
import symtab.Flags
import scala.collection.mutable.{HashMap, HashSet, Map, Set}

import java.io.File

object Analyzer
{
	val PluginName = "sbt-analyzer"
	val CallbackIDOptionName = "callback:"
}
class Analyzer(val global: Global) extends Plugin
{
	import global._
	import Analyzer._
	
	val name = PluginName
	val description = "A plugin to find all concrete instances of a given class and extract dependency information."
	val components = List[PluginComponent](Component)
	
	private var callbackOption: Option[AnalysisCallback] = None
	
	override def processOptions(options: List[String], error: String => Unit)
	{
		for(option <- options)
		{
			if(option.startsWith(CallbackIDOptionName))
				callbackOption = AnalysisCallback(option.substring(CallbackIDOptionName.length).toInt)
			else
				error("Option for sbt analyzer plugin not understood: " + option)
		}
		if(callbackOption.isEmpty)
			error("Callback ID not specified for sbt analyzer plugin.")
	}

	override val optionsHelp: Option[String] =
	{
		val prefix = "  -P:" + name + ":"
		Some(prefix + CallbackIDOptionName + "<callback-id>            Set the callback id.\n")
	}

	/* ================================================== */
	// These two templates abuse scope for source compatibility between Scala 2.7.x and 2.8.x so that a single
	// sbt codebase compiles with both series of versions.
	// In 2.8.x, PluginComponent.runsAfter has type List[String] and the method runsBefore is defined on
	//   PluginComponent with default value Nil.
	// In 2.7.x, runsBefore does not exist on PluginComponent and PluginComponent.runsAfter has type String.
	//
	// Therefore, in 2.8.x, object runsBefore is shadowed by PluginComponent.runsBefore (which is Nil) and so
	//   afterPhase :: runsBefore
	// is equivalent to List[String](afterPhase)
	// In 2.7.x, object runsBefore is not shadowed and so runsAfter has type String.
	private object runsBefore { def :: (s: String) = s }
	private abstract class CompatiblePluginComponent(afterPhase: String) extends PluginComponent
	{
		override val runsAfter = afterPhase :: runsBefore
	}
	/* ================================================== */
	
	private object Component extends CompatiblePluginComponent("jvm")
	{
		val global = Analyzer.this.global
		val phaseName = Analyzer.this.name
		def newPhase(prev: Phase) = new AnalyzerPhase(prev)
	}

	private class AnalyzerPhase(prev: Phase) extends Phase(prev)
	{
		def name = Analyzer.this.name
		def run
		{
			val callback = callbackOption.get
			val projectPath = callback.basePath
			val projectPathString = Path.basePathString(projectPath).getOrElse({error("Could not determine base path for " + projectPath); ""})
			def relativize(file: File) = Path.relativize(projectPath, projectPathString, file)
			
			val outputDir = new File(global.settings.outdir.value)
			val outputPathOption = relativize(outputDir)
			if(outputPathOption.isEmpty)
				error("Output directory " + outputDir.getAbsolutePath + " must be in the project directory.")
			val outputPath = outputPathOption.get
			
			val superclassNames = callback.superclassNames.map(newTermName)
			val superclassesAll =
				for(name <- superclassNames) yield
				{
					try { Some(global.definitions.getClass(name)) }
					catch { case fe: scala.tools.nsc.FatalError => callback.superclassNotFound(name.toString); None }
				}
			val superclasses = superclassesAll.filter(_.isDefined).map(_.get)
			
			for(unit <- currentRun.units)
			{
				// build dependencies structure
				val sourceFile = unit.source.file.file
				val sourcePathOption = relativize(sourceFile)
				if(sourcePathOption.isEmpty)
					error("Source file " + sourceFile.getAbsolutePath + " must be in the project directory.")
				val sourcePath = sourcePathOption.get
				callback.beginSource(sourcePath)
				for(on <- unit.depends)
				{
					val onSource = on.sourceFile
					if(onSource == null)
					{
						classFile(on) match
						{
							case Some(f) =>
							{
								f match
								{
									case ze: ZipArchive#Entry => callback.jarDependency(new File(ze.getArchive.getName), sourcePath)
									case pf: PlainFile =>
									{
										Path.relativize(outputPath, pf.file) match
										{
											case None =>  // dependency is a class file outside of the output directory
												callback.classDependency(pf.file, sourcePath)
											case Some(relativeToOutput) => // dependency is a product of a source not included in this compilation
												callback.productDependency(relativeToOutput, sourcePath)
										}
									}
									case _ => ()
								}
							}
							case None => ()
						}
					}
					else
					{
						for(depPath <- relativize(onSource.file))
							callback.sourceDependency(depPath, sourcePath)
					}
				}
				
				// find subclasses and modules with main methods
				for(clazz @ ClassDef(mods, n, _, _) <- unit.body)
				{
					val sym = clazz.symbol
					if(sym != NoSymbol && mods.isPublic && !mods.isAbstract && !mods.isTrait &&
						 !sym.isImplClass && sym.isStatic && !sym.isNestedClass)
					{
						val isModule = sym.isModuleClass
						for(superclass <- superclasses.filter(sym.isSubClass))
							callback.foundSubclass(sourcePath, sym.fullNameString, superclass.fullNameString, isModule)
						if(isModule && hasMainMethod(sym))
							callback.foundApplication(sourcePath, sym.fullNameString)
					}
				}
				
				// build list of generated classes
				for(iclass <- unit.icode)
				{
					val sym = iclass.symbol
					def addGenerated(separatorRequired: Boolean)
					{
						val classPath = pathOfClass(outputPath, sym, separatorRequired)
						if(classPath.asFile.exists)
							callback.generatedClass(sourcePath, classPath)
					}
					if(sym.isModuleClass && !sym.isImplClass)
					{
						if(isTopLevelModule(sym) && sym.linkedClassOfModule == NoSymbol)
							addGenerated(false)
						addGenerated(true)
					}
					else
						addGenerated(false)
				}
				callback.endSource(sourcePath)
			}
		}
	}
	
	private def classFile(sym: Symbol): Option[AbstractFile] =
	{
		import scala.tools.nsc.symtab.Flags
		val name = sym.fullNameString(java.io.File.separatorChar) + (if (sym.hasFlag(Flags.MODULE)) "$" else "")
		val entry = classPath.root.find(name, false)
		if (entry ne null)
			Some(entry.classFile)
		else if(isTopLevelModule(sym))
		{
			val linked = sym.linkedClassOfModule
			if(linked == NoSymbol)
				None
			else
				classFile(linked)
		}
		else
			None
	}
	
	private def isTopLevelModule(sym: Symbol): Boolean =
		atPhase (currentRun.picklerPhase.next) {
			sym.isModuleClass && !sym.isImplClass && !sym.isNestedClass
		}
	private def pathOfClass(outputPath: Path, s: Symbol, separatorRequired: Boolean): Path =
		pathOfClass(outputPath, s, separatorRequired, ".class")
	private def pathOfClass(outputPath: Path, s: Symbol, separatorRequired: Boolean, postfix: String): Path =
	{
		if(s.owner.isPackageClass && s.isPackageClass)
			packagePath(outputPath, s) / postfix
		else
			pathOfClass(outputPath, s.owner.enclClass, true, s.simpleName + (if(separatorRequired) "$" else "") + postfix)
	}
	private def packagePath(outputPath: Path, s: Symbol): Path =
	{
		if(s.isEmptyPackageClass || s.isRoot)
			outputPath
		else
			packagePath(outputPath, s.owner.enclClass) / s.simpleName.toString
	}
	
	private def hasMainMethod(sym: Symbol): Boolean =
	{
		val main = sym.info.nonPrivateMember(newTermName("main"))//nme.main)
		main.tpe match
		{
			case OverloadedType(pre, alternatives) => alternatives.exists(alt => isVisible(alt) && isMainType(pre.memberType(alt)))
			case tpe => isVisible(main) && isMainType(main.owner.thisType.memberType(main))
		}
	}
	private def isVisible(sym: Symbol) = sym != NoSymbol && sym.isPublic && !sym.isDeferred
	private def isMainType(tpe: Type) =
	{
		tpe match
		{
			// singleArgument is of type Symbol in 2.8.0 and type Type in 2.7.x
			case MethodType(List(singleArgument), result) => isUnitType(result) && isStringArray(singleArgument)
			case _ => false
		}
	}
	private lazy val StringArrayType = appliedType(definitions.ArrayClass.typeConstructor, definitions.StringClass.tpe :: Nil)
	// isStringArray is overloaded to handle the incompatibility between 2.7.x and 2.8.0
	private def isStringArray(tpe: Type): Boolean = tpe.typeSymbol == StringArrayType.typeSymbol
	private def isStringArray(sym: Symbol): Boolean = isStringArray(sym.tpe)
	private def isUnitType(tpe: Type) = tpe.typeSymbol == definitions.UnitClass
}