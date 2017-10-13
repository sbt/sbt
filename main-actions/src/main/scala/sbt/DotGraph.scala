/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import java.io.File
import sbt.internal.inc.Relations

import sbt.internal.util.Relation

import sbt.io.IO

object DotGraph {
  private def fToString(roots: Iterable[File]): (File => String) =
    (x: File) => sourceToString(roots, x)
  def sources(relations: Relations, outputDirectory: File, sourceRoots: Iterable[File]): Unit = {
    val toString = fToString(sourceRoots)
    apply(relations, outputDirectory, toString, toString)
  }
  def packages(relations: Relations, outputDirectory: File, sourceRoots: Iterable[File]): Unit = {
    val packageOnly = (path: String) => {
      val last = path.lastIndexOf(File.separatorChar.toInt)
      val packagePath = (if (last > 0) path.substring(0, last) else path).trim
      if (packagePath.isEmpty) "" else packagePath.replace(File.separatorChar, '.')
    }
    val toString = packageOnly compose fToString(sourceRoots)
    apply(relations, outputDirectory, toString, toString)
  }
  def apply(relations: Relations,
            outputDir: File,
            sourceToString: File => String,
            externalToString: File => String): Unit = {
    def file(name: String) = new File(outputDir, name)
    IO.createDirectory(outputDir)
    generateGraph(file("int-class-deps"),
                  "dependencies",
                  relations.internalClassDep,
                  identity[String],
                  identity[String])
    generateGraph(file("binary-dependencies"),
                  "externalDependencies",
                  relations.libraryDep,
                  externalToString,
                  sourceToString)
  }

  def generateGraph[K, V](file: File,
                          graphName: String,
                          relation: Relation[K, V],
                          keyToString: K => String,
                          valueToString: V => String): Unit = {
    import scala.collection.mutable.{ HashMap, HashSet }
    val mappedGraph = new HashMap[String, HashSet[String]]
    for ((key, values) <- relation.forwardMap; keyString = keyToString(key); value <- values)
      mappedGraph.getOrElseUpdate(keyString, new HashSet[String]) += valueToString(value)

    val mappings =
      for {
        (dependsOn, dependants) <- mappedGraph.toSeq
        dependant <- dependants
        if dependant != dependsOn && !dependsOn.isEmpty && !dependant.isEmpty
      } yield "\"" + dependant + "\" -> \"" + dependsOn + "\""

    val lines =
      ("digraph " + graphName + " {") +:
        mappings :+
        "}"

    IO.writeLines(file, lines)
  }
  def sourceToString(roots: Iterable[File], source: File) =
    relativized(roots, source).trim.stripSuffix(".scala").stripSuffix(".java")

  private def relativized(roots: Iterable[File], path: File): String = {
    val relativized = roots.flatMap(root => IO.relativize(root, path))
    val shortest = (Int.MaxValue /: relativized)(_ min _.length)
    relativized.find(_.length == shortest).getOrElse(path.getName)
  }
}
