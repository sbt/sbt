/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.File
import sbt.internal.inc.Relations
import sbt.internal.util.Relation

import sbt.io.IO

object DotGraph {
  @deprecated("not used", "1.4.0")
  def sources(relations: Relations, outputDirectory: File, sourceRoots: Iterable[File]): Unit = ???
  @deprecated("not used", "1.4.0")
  def packages(relations: Relations, outputDirectory: File, sourceRoots: Iterable[File]): Unit = ???
  @deprecated("not used", "1.4.0")
  def apply(
      relations: Relations,
      outputDir: File,
      sourceToString: File => String,
      externalToString: File => String
  ): Unit = ???

  def generateGraph[K, V](
      file: File,
      graphName: String,
      relation: Relation[K, V],
      keyToString: K => String,
      valueToString: V => String
  ): Unit = {
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
    val shortest = relativized.foldLeft(Int.MaxValue)(_ min _.length)
    relativized.find(_.length == shortest).getOrElse(path.getName)
  }
}
