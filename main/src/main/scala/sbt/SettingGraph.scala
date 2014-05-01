/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah, Eugene Yokota
 */
package sbt

import java.net.URI
import java.io.File
import Def.{ compiled, flattenLocals, ScopedKey }
import Predef.{ any2stringadd => _, _ }

object SettingGraph {
  def apply(structure: BuildStructure, basedir: File, scoped: ScopedKey[_], generation: Int)(implicit display: Show[ScopedKey[_]]): SettingGraph =
    {
      val cMap = flattenLocals(compiled(structure.settings, false)(structure.delegates, structure.scopeLocal, display))
      def loop(scoped: ScopedKey[_], generation: Int): SettingGraph =
        {
          val key = scoped.key
          val scope = scoped.scope
          val definedIn = structure.data.definingScope(scope, key) map { sc => display(ScopedKey(sc, key)) }
          val depends = cMap.get(scoped) match { case Some(c) => c.dependencies.toSet; case None => Set.empty }
          // val related = cMap.keys.filter(k => k.key == key && k.scope != scope)
          // val reverse = reverseDependencies(cMap, scoped)

          SettingGraph(display(scoped), definedIn,
            Project.scopedKeyData(structure, scope, key),
            key.description, basedir,
            depends map { (x: ScopedKey[_]) => loop(x, generation + 1) })
        }
      loop(scoped, generation)
    }
}

case class SettingGraph(name: String,
    definedIn: Option[String],
    data: Option[ScopedKeyData[_]],
    description: Option[String],
    basedir: File,
    depends: Set[SettingGraph]) {
  def dataString: String =
    data map { d =>
      d.settingValue map {
        case f: File => IO.relativize(basedir, f) getOrElse { f.toString }
        case x       => x.toString
      } getOrElse { d.typeName }
    } getOrElse { "" }

  def dependsAscii: String = Graph.toAscii(this,
    (x: SettingGraph) => x.depends.toSeq,
    (x: SettingGraph) => "%s = %s" format (x.definedIn getOrElse { "" }, x.dataString))
}

object Graph {
  // [info] foo
  // [info]   +-bar
  // [info]   | +-baz
  // [info]   |
  // [info]   +-quux
  def toAscii[A](top: A, children: A => Seq[A], display: A => String): String = {
    val defaultWidth = 40
    val maxColumn = math.max(JLine.usingTerminal(_.getWidth), defaultWidth) - 8
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
    def toAsciiLines(node: A, level: Int): Vector[String] = {
      val line = limitLine((twoSpaces * level) + (if (level == 0) "" else "+-") + display(node))
      val cs = Vector(children(node): _*)
      val childLines = cs map { toAsciiLines(_, level + 1) }
      val withBar = childLines.zipWithIndex flatMap {
        case (lines, pos) if pos < (cs.size - 1) => lines map { insertBar(_, 2 * (level + 1)) }
        case (lines, pos) =>
          if (lines.last.trim != "") lines ++ Vector(twoSpaces * (level + 1))
          else lines
      }
      line +: withBar
    }

    toAsciiLines(top, 0).mkString("\n")
  }
}
