/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package server

import java.net.URI
import sjsonnew.shaded.scalajson.ast.unsafe.JValue
import scala.util.{ Left, Right }
import sbt.util.{ SomeJsonWriter, NoJsonWriter }
import sbt.protocol._
import sjsonnew._
import sjsonnew.support.scalajson.unsafe._

object SettingQuery {
  import sbt.internal.util.AttributeKey
  import sbt.internal.util.complete.{ DefaultParsers, Parser }, DefaultParsers._
  import sbt.Def.{ showBuildRelativeKey2, ScopedKey }

  // Similar to Act.ParsedAxis / Act.projectRef / Act.resolveProject except you can't omit the project reference

  sealed trait ParsedExplicitAxis[+T]
  object ParsedExplicitGlobal extends ParsedExplicitAxis[Nothing]
  final class ParsedExplicitValue[T](val value: T) extends ParsedExplicitAxis[T]
  def explicitValue[T](t: Parser[T]): Parser[ParsedExplicitAxis[T]] = t map { v =>
    new ParsedExplicitValue(v)
  }

  def projectRef(
      index: KeyIndex,
      currentBuild: URI
  ): Parser[ParsedExplicitAxis[ResolvedReference]] = {
    val global = token(Act.GlobalIdent ~ '/') ^^^ ParsedExplicitGlobal
    val trailing = '/' !!! "Expected '/' (if selecting a project)"
    global | explicitValue(Act.resolvedReference(index, currentBuild, trailing))
  }

  def resolveProject(parsed: ParsedExplicitAxis[ResolvedReference]): Option[ResolvedReference] =
    parsed match {
      case ParsedExplicitGlobal       => None
      case pv: ParsedExplicitValue[_] => Some(pv.value)
    }

  def scopedKeyFull(
      index: KeyIndex,
      currentBuild: URI,
      defaultConfigs: Option[ResolvedReference] => Seq[String],
      keyMap: Map[String, AttributeKey[?]]
  ): Parser[Seq[Parser[ParsedKey]]] = {
    for {
      rawProject <- projectRef(index, currentBuild)
      proj = resolveProject(rawProject)
      confAmb <- Act.configIdent(
        index.configs(proj),
        index.configIdents(proj),
        index.fromConfigIdent(proj)
      )
      partialMask = ScopeMask(true, confAmb.isExplicit, false, false)
    } yield Act.taskKeyExtra(index, defaultConfigs, keyMap, proj, confAmb, partialMask)
  }

  def scopedKeySelected(
      index: KeyIndex,
      currentBuild: URI,
      defaultConfigs: Option[ResolvedReference] => Seq[String],
      keyMap: Map[String, AttributeKey[?]],
      data: Def.Settings
  ): Parser[ParsedKey] =
    scopedKeyFull(index, currentBuild, defaultConfigs, keyMap) flatMap { choices =>
      Act.select(choices, data)(using showBuildRelativeKey2(currentBuild))
    }

  def scopedKey(
      index: KeyIndex,
      currentBuild: URI,
      defaultConfigs: Option[ResolvedReference] => Seq[String],
      keyMap: Map[String, AttributeKey[?]],
      data: Def.Settings
  ): Parser[ScopedKey[?]] =
    scopedKeySelected(index, currentBuild, defaultConfigs, keyMap, data).map(_.key)

  def scopedKeyParser(structure: BuildStructure): Parser[ScopedKey[?]] =
    scopedKey(
      structure.index.keyIndex,
      structure.root,
      structure.extra.configurationsForAxis,
      structure.index.keyMap,
      structure.data
    )

  def getSettingValue[A](structure: BuildStructure, key: Def.ScopedKey[A]): Either[String, A] =
    structure.data
      .get(key)
      .toRight(s"Key ${Def.displayFull(key)} not found")
      .flatMap {
        case _: Task[_] => Left(s"Key ${Def.displayFull(key)} is a task, can only query settings")
        case _: InputTask[_] =>
          Left(s"Key ${Def.displayFull(key)} is an input task, can only query settings")
        case x => Right(x)
      }

  def getJsonWriter[A](key: AttributeKey[A]): Either[String, JsonWriter[A]] =
    key.optJsonWriter match {
      case SomeJsonWriter(jw) => Right(jw)
      case NoJsonWriter()     => Left(s"JsonWriter for ${key.tag} not found")
    }

  def toJson[A: JsonWriter](x: A): JValue = Converter toJsonUnsafe x

  def getSettingJsonValue[A](
      structure: BuildStructure,
      key: Def.ScopedKey[A]
  ): Either[String, JValue] =
    getSettingValue(structure, key) flatMap (value =>
      getJsonWriter(key.key) map { case given JsonWriter[A] =>
        toJson(value)
      }
    )

  def handleSettingQueryEither(
      req: SettingQuery,
      structure: BuildStructure
  ): Either[String, SettingQuerySuccess] = {
    val key = Parser.parse(req.setting, scopedKeyParser(structure))

    for {
      key <- key
      json <- getSettingJsonValue(structure, key)
    } yield SettingQuerySuccess(json, key.key.tag.toString)
  }

  def handleSettingQuery(req: SettingQuery, structure: BuildStructure): SettingQueryResponse =
    handleSettingQueryEither(req, structure) match {
      case Right(x) => x
      case Left(s)  => SettingQueryFailure(s)
    }
}
