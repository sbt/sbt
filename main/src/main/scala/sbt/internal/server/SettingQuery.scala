/*
 * Copyright (C) 2016-2017 Lightbend Inc. <http://www.lightbend.com>
 */
package sbt
package internal
package server

import java.net.URI
import scala.util.{ Left, Right }
import sbt.protocol._
import sjsonnew._
import sjsonnew.support.scalajson.unsafe._

object SettingQuery {
  import sbt.internal.util.{ AttributeKey, Settings }
  import sbt.internal.util.complete.{ DefaultParsers, Parser }, DefaultParsers._
  import sbt.Def.{ showBuildRelativeKey, ScopedKey }

  // Similar to Act.ParsedAxis / Act.projectRef / Act.resolveProject except you can't omit the project reference

  sealed trait ParsedExplicitAxis[+T]
  final object ParsedExplicitGlobal extends ParsedExplicitAxis[Nothing]
  final class ParsedExplicitValue[T](val value: T) extends ParsedExplicitAxis[T]
  def explicitValue[T](t: Parser[T]): Parser[ParsedExplicitAxis[T]] = t map { v => new ParsedExplicitValue(v) }

  def projectRef(index: KeyIndex, currentBuild: URI): Parser[ParsedExplicitAxis[ResolvedReference]] = {
    val global = token(Act.GlobalString ~ '/') ^^^ ParsedExplicitGlobal
    val trailing = '/' !!! "Expected '/' (if selecting a project)"
    global | explicitValue(Act.resolvedReference(index, currentBuild, trailing))
  }

  def resolveProject(parsed: ParsedExplicitAxis[ResolvedReference]): Option[ResolvedReference] = parsed match {
    case ParsedExplicitGlobal       => None
    case pv: ParsedExplicitValue[_] => Some(pv.value)
  }

  def scopedKeyFull(
    index: KeyIndex,
    currentBuild: URI,
    defaultConfigs: Option[ResolvedReference] => Seq[String],
    keyMap: Map[String, AttributeKey[_]]
  ): Parser[Seq[Parser[ParsedKey]]] = {
    for {
      rawProject <- projectRef(index, currentBuild)
      proj = resolveProject(rawProject)
      confAmb <- Act.config(index configs proj)
      partialMask = ScopeMask(true, confAmb.isExplicit, false, false)
    } yield Act.taskKeyExtra(index, defaultConfigs, keyMap, proj, confAmb, partialMask)
  }

  def scopedKeySelected(
    index: KeyIndex,
    currentBuild: URI,
    defaultConfigs: Option[ResolvedReference] => Seq[String],
    keyMap: Map[String, AttributeKey[_]],
    data: Settings[Scope]
  ): Parser[ParsedKey] =
    scopedKeyFull(index, currentBuild, defaultConfigs, keyMap) flatMap { choices =>
      Act.select(choices, data)(showBuildRelativeKey(currentBuild, index.buildURIs.size > 1))
    }

  def scopedKey(
    index: KeyIndex,
    currentBuild: URI,
    defaultConfigs: Option[ResolvedReference] => Seq[String],
    keyMap: Map[String, AttributeKey[_]],
    data: Settings[Scope]
  ): Parser[ScopedKey[_]] =
    scopedKeySelected(index, currentBuild, defaultConfigs, keyMap, data).map(_.key)

  def scopedKeyParser(structure: BuildStructure): Parser[ScopedKey[_]] =
    scopedKey(
      structure.index.keyIndex,
      structure.root,
      structure.extra.configurationsForAxis,
      structure.index.keyMap,
      structure.data
    )

  def getSettingValue[A](structure: BuildStructure, key: Def.ScopedKey[A]): Either[String, A] =
    structure.data.get(key.scope, key.key)
      .toRight(s"Key ${Def displayFull key} not found")
      .flatMap {
        case _: Task[_]      => Left(s"Key ${Def displayFull key} is a task, can only query settings")
        case _: InputTask[_] => Left(s"Key ${Def displayFull key} is an input task, can only query settings")
        case x               => Right(x)
      }

  def toJsonStringStrict[A: Manifest](x: A): Either[String, String] =
    JsonFormatRegistry.lookup[A]
      .toRight(s"JsonWriter for ${manifest[A]} not found")
      .map(implicit jsonWriter => CompactPrinter(Converter.toJsonUnsafe(x)))

  def toJsonString[A: Manifest](x: A): String =
    toJsonStringStrict(x) match {
      case Right(s) => s
      case Left(_)  => x.toString
    }

  def getSettingJsonStringValue[A](structure: BuildStructure, key: Def.ScopedKey[A]): Either[String, String] =
    getSettingValue(structure, key) map (toJsonString(_)(key.key.manifest))

  def handleSettingQuery(req: SettingQuery, structure: BuildStructure): SettingQueryResponse = {
    val key = Parser.parse(req.setting, scopedKeyParser(structure))

    val result: Either[String, String] = key flatMap (getSettingJsonStringValue(structure, _))

    SettingQueryResponse(result.merge)
  }
}
