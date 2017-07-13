package sbt.internal

import sbt._
import sbt.Def.Setting
import sbt.internal.util._
import sbt.librarymanagement.ModuleID
import sjsonnew.LList.:*:
import sjsonnew.{ IsoLList, JsonFormat, LList, LNil }

import scala.util.control.NonFatal

object ModulePositions extends ModulePositionsCodec {

  /** Associates a source position to every defined library dependency setting.
   *
   * @param state The state.
   * @param currentProject The current project for which library dependencies are positioned.
   * @param libraryDependencies The library dependencies setting.
   * @return A map of module ids to source positions (where the module ids are defined).
   */
  def modulesToPositions(
      state: State,
      currentProject: ProjectReference,
      libraryDependencies: SettingKey[Seq[ModuleID]]
  ): Map[ModuleID, SourcePosition] = {
    def getModulesAndPositions: Seq[(ModuleID, SourcePosition)] = {
      val buildStructure = Project.extract(state).structure
      val scopedKey = (libraryDependencies in (Scope.GlobalScope in currentProject)).scopedKey
      val emptyScopes = buildStructure.data set (scopedKey.scope, scopedKey.key, Nil)
      val settings = buildStructure.settings.filter((s: Setting[_]) =>
        (s.key.key == libraryDependencies.key) && (s.key.scope.project == Select(currentProject)))
      for {
        libraryDependencies <- settings.asInstanceOf[Seq[Setting[Seq[ModuleID]]]]
        moduleID <- libraryDependencies.init.evaluate(emptyScopes)
      } yield moduleID -> libraryDependencies.pos
    }

    try Map(getModulesAndPositions: _*)
    catch { case NonFatal(_) => Map() }
  }
}

trait ModulePositionsCodec {
  import sbt.librarymanagement.LibraryManagementCodec._
  implicit val NoPositionFormat: JsonFormat[NoPosition.type] = asSingleton(NoPosition)
  implicit val LinePositionFormat: IsoLList.Aux[LinePosition, String :*: Int :*: LNil] =
    LList.iso((l: LinePosition) => ("path", l.path) :*: ("startLine", l.startLine) :*: LNil,
              (in: String :*: Int :*: LNil) => LinePosition(in.head, in.tail.head))

  implicit val LineRangeFormat: IsoLList.Aux[LineRange, Int :*: Int :*: LNil] = LList.iso(
    (l: LineRange) => ("start", l.start) :*: ("end", l.end) :*: LNil,
    (in: Int :*: Int :*: LNil) => LineRange(in.head, in.tail.head))

  implicit val RangePositionFormat: IsoLList.Aux[RangePosition, String :*: LineRange :*: LNil] =
    LList.iso((r: RangePosition) => ("path", r.path) :*: ("range", r.range) :*: LNil,
              (in: String :*: LineRange :*: LNil) => RangePosition(in.head, in.tail.head))

  implicit val SourcePositionFormat: JsonFormat[SourcePosition] =
    unionFormat3[SourcePosition, NoPosition.type, LinePosition, RangePosition]

  implicit val midJsonKeyFmt: sjsonnew.JsonKeyFormat[ModuleID] =
    sbt.internal.librarymanagement.UnsafeLibraryManagementCodec.moduleIdJsonKeyFormat
}
