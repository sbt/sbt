/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */
package sbt

import java.io.{File, Writer}

object DotGraph
{
	def sources(analysis: BasicCompileAnalysis, outputDirectory: Path, sourceRoots: Iterable[Path], log: Logger) =
	{
		val roots = sourceRoots.toList.map(_.asFile)
		val toString = (x: File) => sourceToString(roots, x)
		apply(analysis, outputDirectory, toString, toString, log)
	}
	def packages(analysis: BasicCompileAnalysis, outputDirectory: Path, sourceRoots: Iterable[Path], log: Logger) =
	{
		val roots = sourceRoots.toList.map(_.asFile)
		val packageOnly = (path: String) =>
		{
			val last = path.lastIndexOf(File.separatorChar)
			val packagePath = (if(last > 0) path.substring(0, last) else path).trim
			if(packagePath.isEmpty) "" else packagePath.replace(File.separatorChar, '.')
		}
		val toString = packageOnly compose ((x: File) => sourceToString(roots, x))
		apply(analysis, outputDirectory, toString, toString, log)
	}
	def apply(analysis: BasicCompileAnalysis, outputDirectory: Path, sourceToString: File => String, externalToString: File => String, log: Logger) =
	{
		val outputDir = outputDirectory.asFile
		
		def generateGraph[Key, Value](fileName: String, graphName: String, graph: Iterable[(Key, scala.collection.Set[Value])],
			keyToString: Key => String, valueToString: Value => String) =
		{
			import scala.collection.mutable.{HashMap, HashSet}
			val mappedGraph = new HashMap[String, HashSet[String]]
			for( (key, values) <- graph; keyString = keyToString(key); value <- values)
				mappedGraph.getOrElseUpdate(keyString, new HashSet[String]) += valueToString(value)

			FileUtilities.write(new File(outputDir, fileName), log) { (writer: Writer) =>
			
				def writeLine(line: String) = FileUtilities.writeLine(writer, line)
				writeLine("digraph " + graphName + " {")
				for( (dependsOn, dependants) <- mappedGraph; dependant <- dependants)
				{
					if(dependant != dependsOn && !dependsOn.isEmpty && !dependant.isEmpty)
						writeLine("\"" + dependant + "\" -> \"" + dependsOn + "\"")
				}
				writeLine("}")
				None
			}
		}
		val srcToString = (p: Path) => sourceToString(p.asFile)
		FileUtilities.createDirectory(outputDir, log) orElse
		generateGraph(BasicAnalysis.DependenciesFileName, "dependencies", analysis.allDependencies,
			srcToString, srcToString) orElse
		generateGraph(BasicAnalysis.ExternalDependenciesFileName, "externalDependencies", analysis.allExternalDependencies,
			externalToString, srcToString)
	}
	def sourceToString(roots: List[File], source: File) =
	{
		val rawName = relativized(roots, source).trim
		if(rawName.endsWith(".scala"))
			rawName.substring(0, rawName.length - ".scala".length)
		else
			rawName
	}
	private def relativized(roots: List[File], path: File): String =
	{
		val relativized = roots.flatMap(root => Path.relativize(root, path))
		val shortest = (Int.MaxValue /: relativized)(_ min _.length)
		relativized.find(_.length == shortest).getOrElse(path.getName)
	}
}