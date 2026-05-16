/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import sbt.util.Show
import java.io.File

import Def.{ ScopedKey, compiled, flattenLocals }
import sbt.ProjectExtra.scopedKeyData
import sbt.io.IO

object SettingGraph {
  def apply(structure: BuildStructure, basedir: File, scoped: ScopedKey[?], generation: Int)(using
      display: Show[ScopedKey[?]]
  ): SettingGraph = {
    val cMap = flattenLocals(
      compiled(structure.settings, false)(using structure.delegates, structure.scopeLocal, display)
    )
    def loop(scoped: ScopedKey[?], generation: Int): SettingGraph = {
      val data = Project.scopedKeyData(structure, scoped)
      val definedIn = data.map(d => display.show(d.definingKey))
      val depends = cMap.get(scoped) match {
        case Some(c) => c.dependencies.toSet; case None => Set.empty
      }
      // val related = cMap.keys.filter(k => k.key == key && k.scope != scope)
      // val reverse = reverseDependencies(cMap, scoped)

      SettingGraph(
        display.show(scoped),
        definedIn,
        data,
        scoped.key.description,
        basedir,
        depends map { (x: ScopedKey[?]) =>
          loop(x, generation + 1)
        }
      )
    }
    loop(scoped, generation)
  }
}

case class SettingGraph(
    name: String,
    definedIn: Option[String],
    data: Option[ScopedKeyData[?]],
    description: Option[String],
    basedir: File,
    depends: Set[SettingGraph]
) {
  def dataString: String =
    data map { d =>
      d.settingValue map {
        case f: File => IO.relativize(basedir, f) getOrElse { f.toString }
        case x       => x.toString
      } getOrElse { d.typeName }
    } getOrElse { "" }

  def dependsAscii(defaultWidth: Int): String = Graph.toAscii(
    this,
    (x: SettingGraph) => x.depends.toSeq.sortBy(_.name),
    (x: SettingGraph) => s"${x.definedIn getOrElse { "" }} = ${x.dataString}",
    defaultWidth
  )
}

object Graph {
  // [info] foo
  // [info]   +-bar
  // [info]   | +-baz
  // [info]   |
  // [info]   +-quux
  def toAscii[A](
      top: A,
      children: A => Seq[A],
      display: A => String,
      maxColumn: Int
  ): String = {
    val twoSpaces = " " + " " // prevent accidentally being converted into a tab
    def limitLine(s: String): String =
      if (s.length > maxColumn) s.slice(0, maxColumn - 2) + ".."
      else s
    def insertBar(s: String, at: Int): String =
      if (at < s.length)
        s.slice(0, at) +
          (s(at).toString match {
            case " " => "|"
            case x   => x
          }) +
          s.slice(at + 1, s.length)
      else s
    // Owned by toAsciiLines; grows monotonically over one render.
    import scala.collection.mutable
    val visited = mutable.Set.empty[A]
    def toAsciiLines(node: A, level: Int, parents: Set[A]): Vector[String] = {
      val prefix = if (level == 0) "" else "+-"
      if (parents contains node) // cycle
        Vector(limitLine((twoSpaces * level) + "#-" + display(node) + " (cycle)"))
      else if (visited contains node)
        // `prefix` is always "+-" here in practice (root can't re-enter),
        // but mirror the level-0 form for symmetry.
        Vector(limitLine((twoSpaces * level) + prefix + display(node) + " (*)"))
      else {
        visited += node
        val line = limitLine((twoSpaces * level) + prefix + display(node))
        val cs = Vector(children(node)*)
        val childLines = cs map {
          toAsciiLines(_, level + 1, parents + node)
        }
        val withBar = childLines.zipWithIndex flatMap {
          case (lines, pos) if pos < (cs.size - 1) =>
            lines map {
              insertBar(_, 2 * (level + 1))
            }
          case (lines, pos) =>
            if (lines.last.trim != "") lines ++ Vector(twoSpaces * (level + 1))
            else lines
        }
        line +: withBar
      }
    }

    toAsciiLines(top, 0, Set.empty).mkString("\n")
  }
}
