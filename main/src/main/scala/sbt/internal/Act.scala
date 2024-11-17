/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import Def.{ showRelativeKey2, ScopedKey }
import Keys.sessionSettings
import sbt.internal.util.complete.{ DefaultParsers, Parser }
import Aggregation.{ KeyValue, Values }
import DefaultParsers._
import sbt.internal.util.Types.idFun
import sbt.ProjectExtra.{ failure => _, * }
import java.net.URI
import sbt.internal.CommandStrings.{ MultiTaskCommand, ShowCommand, PrintCommand }
import sbt.internal.util.{
  AttributeEntry,
  AttributeKey,
  AttributeMap,
  IMap,
  MessageOnlyException,
  Util,
}
import sbt.util.Show
import scala.collection.mutable

final class ParsedKey(val key: ScopedKey[?], val mask: ScopeMask):
  override def equals(o: Any): Boolean =
    this.eq(o.asInstanceOf[AnyRef]) || (o match {
      case x: ParsedKey => (this.key == x.key) && (this.mask == x.mask)
      case _            => false
    })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.ParsedKey".##) + this.key.##)) + this.mask.##
  }
end ParsedKey

object Act {
  private[sbt] val GlobalIdent = "Global"
  private[sbt] val ZeroIdent = "Zero"
  private[sbt] val ThisBuildIdent = "ThisBuild"

  // new separator for unified shell syntax. this allows optional whitespace around /.
  private[sbt] val spacedSlash: Parser[Unit] =
    token(OptSpace ~> '/' <~ OptSpace).examples("/").map(_ => ())

  private[sbt] val slashSeq: Seq[String] = Seq("/")

  type KeysParser = Parser[Seq[ScopedKey[Any]]]
  type KeysParserFilter = Parser[Seq[(ScopedKey[Any], Option[ProjectQuery])]]

  // this does not take aggregation into account
  def scopedKey(
      index: KeyIndex,
      current: ProjectRef,
      defaultConfigs: Option[ResolvedReference] => Seq[String],
      keyMap: Map[String, AttributeKey[?]],
      data: Def.Settings
  ): Parser[ScopedKey[Any]] =
    scopedKeySelected(index, current, defaultConfigs, keyMap, data, askProject = true)
      .map(_.key.asInstanceOf[ScopedKey[Any]])

  // the index should be an aggregated index for proper tab completion
  def scopedKeyAggregated(
      current: ProjectRef,
      defaultConfigs: Option[ResolvedReference] => Seq[String],
      structure: BuildStructure
  ): KeysParser =
    for (
        selected <- scopedKeySelected(
          structure.index.aggregateKeyIndex,
          current,
          defaultConfigs,
          structure.index.keyMap,
          structure.data,
          askProject = true,
        )
      )
      yield Aggregation.aggregate(
        selected.key.asInstanceOf[ScopedKey[Any]],
        selected.mask,
        structure.extra
      )

  def scopedKeyAggregatedFilter(
      current: ProjectRef,
      defaultConfigs: Option[ResolvedReference] => Seq[String],
      structure: BuildStructure
  ): KeysParserFilter =
    for
      optQuery <- queryOption.?
      selected <- scopedKeySelected(
        structure.index.aggregateKeyIndex,
        current,
        defaultConfigs,
        structure.index.keyMap,
        structure.data,
        askProject = optQuery.isEmpty,
      )
    yield Aggregation
      .aggregate(selected.key, selected.mask, structure.extra)
      .map(k => k.asInstanceOf[ScopedKey[Any]] -> optQuery)

  private def queryOption: Parser[ProjectQuery] =
    ProjectQuery.parser <~ spacedSlash

  def scopedKeySelected(
      index: KeyIndex,
      current: ProjectRef,
      defaultConfigs: Option[ResolvedReference] => Seq[String],
      keyMap: Map[String, AttributeKey[?]],
      data: Def.Settings,
      askProject: Boolean,
  ): Parser[ParsedKey] =
    scopedKeyFull(index, current, defaultConfigs, keyMap, askProject = askProject).flatMap {
      choices =>
        select(choices, data)(using showRelativeKey2(current))
    }

  def scopedKeyFull(
      index: KeyIndex,
      current: ProjectRef,
      defaultConfigs: Option[ResolvedReference] => Seq[String],
      keyMap: Map[String, AttributeKey[?]],
      askProject: Boolean,
  ): Parser[Seq[Parser[ParsedKey]]] = {
    val confParserCache: mutable.Map[Option[sbt.ResolvedReference], Parser[ParsedAxis[String]]] =
      mutable.Map.empty
    def fullKey =
      for
        rawProject <-
          if askProject then optProjectRef(index, current)
          else success(Omitted)
        proj = resolveProject(rawProject, current)
        confAmb <- confParserCache.getOrElseUpdate(
          proj,
          configIdent(
            index.configs(proj),
            index.configIdents(proj),
            index.fromConfigIdent(proj)
          )
        )
        partialMask = ScopeMask(rawProject.isExplicit, confAmb.isExplicit, false, false)
      yield taskKeyExtra(index, defaultConfigs, keyMap, proj, confAmb, partialMask)

    val globalIdent = token(GlobalIdent ~ spacedSlash) ^^^ ParsedGlobal
    def globalKey =
      for {
        g <- globalIdent
      } yield taskKeyExtra(
        index,
        defaultConfigs,
        keyMap,
        None,
        ParsedZero,
        ScopeMask(true, true, false, false),
      )

    globalKey | fullKey
  }

  def taskKeyExtra(
      index: KeyIndex,
      defaultConfigs: Option[ResolvedReference] => Seq[String],
      keyMap: Map[String, AttributeKey[?]],
      proj: Option[ResolvedReference],
      confAmb: ParsedAxis[String],
      baseMask: ScopeMask,
  ): Seq[Parser[ParsedKey]] =
    for {
      conf <- configs(confAmb, defaultConfigs, proj, index)
    } yield for {
      taskAmb <- taskAxis(index.tasks(proj, conf), keyMap)
      task = resolveTask(taskAmb)
      key <- key(index, proj, conf, task, keyMap)
      extra <- extraAxis(keyMap, IMap.empty)
    } yield {
      val mask = baseMask.copy(task = taskAmb.isExplicit, extra = true)
      ParsedKey(makeScopedKey(proj, conf, task, extra, key), mask)
    }

  def makeScopedKey(
      proj: Option[ResolvedReference],
      conf: Option[String],
      task: Option[AttributeKey[?]],
      extra: ScopeAxis[AttributeMap],
      key: AttributeKey[?]
  ): ScopedKey[?] =
    ScopedKey(
      Scope(toAxis(proj, Zero), toAxis(conf map ConfigKey.apply, Zero), toAxis(task, Zero), extra),
      key
    )

  def select(allKeys: Seq[Parser[ParsedKey]], data: Def.Settings)(using
      show: Show[ScopedKey[?]]
  ): Parser[ParsedKey] =
    seq(allKeys) flatMap { ss =>
      val default: Parser[ParsedKey] = ss.headOption match
        case None    => noValidKeys
        case Some(x) => success(x)
      selectFromValid(ss filter isValid(data), default)
    }

  def selectFromValid(ss: Seq[ParsedKey], default: Parser[ParsedKey])(using
      show: Show[ScopedKey[?]]
  ): Parser[ParsedKey] =
    selectByTask(selectByConfig(ss)) match {
      case Seq()       => default
      case Seq(single) => success(single)
      case multi       => failure("Ambiguous keys: " + showAmbiguous(keys(multi)))
    }
  private def keys(ss: Seq[ParsedKey]): Seq[ScopedKey[?]] = ss.map(_.key)
  def selectByConfig(ss: Seq[ParsedKey]): Seq[ParsedKey] =
    ss match {
      case Seq() => Nil
      case Seq(x, tail @ _*) => // select the first configuration containing a valid key
        tail.takeWhile(_.key.scope.config == x.key.scope.config) match {
          case Seq() => x :: Nil
          case xs    => x +: xs
        }
    }
  def selectByTask(ss: Seq[ParsedKey]): Seq[ParsedKey] = {
    val (selects, zeros) = ss.partition(_.key.scope.task.isSelect)
    if (zeros.nonEmpty) zeros else selects
  }

  def noValidKeys = failure("No such key.")

  def showAmbiguous(keys: Seq[ScopedKey[?]])(using show: Show[ScopedKey[?]]): String =
    keys.take(3).map(x => show.show(x)).mkString("", ", ", if (keys.size > 3) ", ..." else "")

  def isValid(data: Def.Settings)(parsed: ParsedKey): Boolean = data.contains(parsed.key)

  def examples(p: Parser[String], exs: Set[String], label: String): Parser[String] =
    (p !!! ("Expected " + label)).examples(exs)

  def examplesStrict(p: Parser[String], exs: Set[String], label: String): Parser[String] =
    filterStrings(examples(p, exs, label), exs, label)

  def optionalAxis[T](p: Parser[T], ifNone: ScopeAxis[T]): Parser[ScopeAxis[T]] =
    p.? map { opt =>
      toAxis(opt, ifNone)
    }

  def toAxis[T](opt: Option[T], ifNone: ScopeAxis[T]): ScopeAxis[T] =
    opt match { case Some(t) => Select(t); case None => ifNone }

  // New configuration parser that's able to parse configuration ident trailed by slash.
  private[sbt] def configIdent(
      confs: Set[String],
      idents: Set[String],
      fromIdent: String => String
  ): Parser[ParsedAxis[String]] =
    val sep: Parser[Unit] = spacedSlash !!! "Expected '/'"
    token(
      ((ZeroIdent ^^^ ParsedZero) <~ sep)
        | (value(examples(CapitalizedID, idents, "configuration ident").map(fromIdent)) <~ sep)
    ) ?? Omitted

  def configs(
      explicit: ParsedAxis[String],
      defaultConfigs: Option[ResolvedReference] => Seq[String],
      proj: Option[ResolvedReference],
      index: KeyIndex
  ): Seq[Option[String]] =
    explicit match {
      case Omitted =>
        None +: defaultConfigurations(proj, index, defaultConfigs).flatMap(
          nonEmptyConfig(index, proj)
        )
      case ParsedZero | ParsedGlobal => None :: Nil
      case pv: ParsedValue[x]        => Some(pv.value) :: Nil
    }

  def defaultConfigurations(
      proj: Option[ResolvedReference],
      index: KeyIndex,
      defaultConfigs: Option[ResolvedReference] => Seq[String]
  ): Seq[String] =
    if index.exists(proj) then defaultConfigs(proj) else Nil

  def nonEmptyConfig(
      index: KeyIndex,
      proj: Option[ResolvedReference]
  ): String => Seq[Option[String]] =
    config => if (index.isEmpty(proj, Some(config))) Nil else Some(config) :: Nil

  def key(
      index: KeyIndex,
      proj: Option[ResolvedReference],
      conf: Option[String],
      task: Option[AttributeKey[?]],
      keyMap: Map[String, AttributeKey[?]]
  ): Parser[AttributeKey[?]] = {
    def dropHyphenated(keys: Set[String]): Set[String] = keys.filterNot(Util.hasHyphen)
    def keyParser(keys: Set[String]): Parser[AttributeKey[?]] =
      token((ID !!! "Expected key").examples(dropHyphenated(keys))).flatMap: keyString =>
        getKey(keyMap, keyString, idFun)

    // Fixes sbt/sbt#2460 and sbt/sbt#2851
    // The parser already accepts build-level keys.
    // This queries the key index so tab completion will list the build-level keys.
    val buildKeys: Set[String] =
      proj match {
        case Some(ProjectRef(uri, _)) => index.keys(Some(BuildRef(uri)), conf, task)
        case _                        => Set()
      }
    val globalKeys: Set[String] =
      proj match {
        case Some(_) => index.keys(None, conf, task)
        case _       => Set()
      }
    val keys: Set[String] = index.keys(proj, conf, task) ++ buildKeys ++ globalKeys
    keyParser(keys)
  }

  def getKey[T](
      keyMap: Map[String, AttributeKey[?]],
      keyString: String,
      f: AttributeKey[?] => T
  ): Parser[T] =
    keyMap.get(keyString) match {
      case Some(k) => success(f(k))
      case None    => failure(Command.invalidValue("key", keyMap.keys)(keyString))
    }

  val spacedComma = token(OptSpace ~ ',' ~ OptSpace)

  def extraAxis(
      knownKeys: Map[String, AttributeKey[?]],
      knownValues: IMap[AttributeKey, Set]
  ): Parser[ScopeAxis[AttributeMap]] = {
    val extrasP = extrasParser(knownKeys, knownValues)
    val extras =
      token('(', hide = (x: Int) => x == 1 && knownValues.isEmpty) ~> extrasP <~ token(')')
    optionalAxis(extras, Zero)
  }

  def taskAxis(
      tasks: Set[AttributeKey[?]],
      allKnown: Map[String, AttributeKey[?]],
  ): Parser[ParsedAxis[AttributeKey[?]]] = {
    val taskSeq = tasks.toSeq
    def taskKeys(f: AttributeKey[?] => String): Seq[(String, AttributeKey[?])] =
      taskSeq.map(key => (f(key), key))
    val normKeys = taskKeys(_.label)
    val valid = allKnown ++ normKeys
    val suggested = normKeys.map(_._1).toSet
    val keyP = filterStrings(examples(ID, suggested, "key"), valid.keySet, "key").map(valid)

    (token(
      value(keyP) | (ZeroIdent ^^^ ParsedZero)
    ) <~ spacedSlash) ?? Omitted
  }

  def resolveTask(task: ParsedAxis[AttributeKey[?]]): Option[AttributeKey[?]] =
    task match {
      case ParsedZero | ParsedGlobal | Omitted        => None
      case t: ParsedValue[AttributeKey[_]] @unchecked => Some(t.value)
    }

  def filterStrings(base: Parser[String], valid: Set[String], label: String): Parser[String] =
    base.filter(valid, Command.invalidValue(label, valid))

  def extrasParser(
      knownKeys: Map[String, AttributeKey[?]],
      knownValues: IMap[AttributeKey, Set]
  ): Parser[AttributeMap] = {
    val validKeys = knownKeys.filter { case (_, key) => knownValues.get(key).exists(_.nonEmpty) }
    if (validKeys.isEmpty)
      failure("No valid extra keys.")
    else
      rep1sep(extraParser(validKeys, knownValues), spacedComma).map(AttributeMap.apply)
  }

  def extraParser(
      knownKeys: Map[String, AttributeKey[?]],
      knownValues: IMap[AttributeKey, Set]
  ): Parser[AttributeEntry[?]] = {
    val keyp = knownIDParser(knownKeys, "Not a valid extra key") <~ token(':' ~ OptSpace)
    keyp flatMap { case key: AttributeKey[t] =>
      val valueMap: Map[String, t] = knownValues(key).map(v => (v.toString, v)).toMap
      knownIDParser(valueMap, "extra value") map { value =>
        AttributeEntry(key, value)
      }
    }
  }
  def knownIDParser[T](knownKeys: Map[String, T], label: String): Parser[T] =
    token(examplesStrict(ID, knownKeys.keys.toSet, label)).map(knownKeys)

  def knownPluginParser[T](knownPlugins: Map[String, T], label: String): Parser[T] = {
    val pluginLabelParser = rep1sep(ID, '.').map(_.mkString("."))
    token(examplesStrict(pluginLabelParser, knownPlugins.keys.toSet, label)).map(knownPlugins)
  }

  def projectRef(index: KeyIndex, currentBuild: URI): Parser[ParsedAxis[ResolvedReference]] = {
    val zeroIdent = token(ZeroIdent ~ spacedSlash) ^^^ ParsedZero
    val thisBuildIdent = value(token(ThisBuildIdent ~ spacedSlash) ^^^ BuildRef(currentBuild))
    val trailing = spacedSlash !!! "Expected '/' (if selecting a project)"
    zeroIdent | thisBuildIdent |
      value(resolvedReferenceIdent(index, currentBuild, trailing)) |
      value(resolvedReference(index, currentBuild, trailing))
  }

  private[sbt] def resolvedReferenceIdent(
      index: KeyIndex,
      currentBuild: URI,
      trailing: Parser[?]
  ): Parser[ResolvedReference] = {
    def projectID(uri: URI) =
      token(
        DQuoteChar ~> examplesStrict(
          ID,
          index.projects(uri),
          "project ID"
        ) <~ DQuoteChar <~ OptSpace <~ ")" <~ trailing
      )
    def projectRef(uri: URI) = projectID(uri) map { id =>
      ProjectRef(uri, id)
    }

    val uris = index.buildURIs
    val resolvedURI = Uri(uris).map(uri => Scope.resolveBuild(currentBuild, uri))

    val buildRef = token(
      "ProjectRef(" ~> OptSpace ~> "uri(" ~> OptSpace ~> DQuoteChar ~>
        resolvedURI <~ DQuoteChar <~ OptSpace <~ ")" <~ spacedComma
    )
    buildRef flatMap { uri =>
      projectRef(uri)
    }
  }

  def resolvedReference(
      index: KeyIndex,
      currentBuild: URI,
      trailing: Parser[?]
  ): Parser[ResolvedReference] = {
    def projectID(uri: URI) =
      token(examplesStrict(ID, index.projects(uri), "project ID") <~ trailing)
    def projectRef(uri: URI) = projectID(uri) map { id =>
      ProjectRef(uri, id)
    }

    val uris = index.buildURIs
    val resolvedURI = Uri(uris).map(uri => Scope.resolveBuild(currentBuild, uri))
    val buildRef = token('{' ~> resolvedURI <~ '}').?

    buildRef flatMap {
      case None      => projectRef(currentBuild)
      case Some(uri) => projectRef(uri) | token(trailing ^^^ BuildRef(uri))
    }
  }
  def optProjectRef(index: KeyIndex, current: ProjectRef): Parser[ParsedAxis[ResolvedReference]] =
    projectRef(index, current.build) ?? Omitted

  def resolveProject(
      parsed: ParsedAxis[ResolvedReference],
      current: ProjectRef
  ): Option[ResolvedReference] =
    parsed match {
      case Omitted             => Some(current)
      case ParsedZero          => None
      case ParsedGlobal        => None
      case pv: ParsedValue[rr] => Some(pv.value)
    }

  def actParser(s: State): Parser[() => State] = requireSession(s, actParser0(s))

  private def actParser0(state: State): Parser[() => State] =
    val extracted = Project.extract(state)
    import extracted.{ showKey, structure }
    actionParser.flatMap: action =>
      val akp = aggregatedKeyParserFilter(extracted)
      // If the task name matches, but the query is empty, we should succeed the parser,
      // but fail the task. Otherwise, the composed parser would think we made a typo.
      def emptyResult: Parser[() => State] =
        Parser.success(() => throw MessageOnlyException("query result is empty"))
      def evaluate(kvs: Seq[ScopedKey[?]]): Parser[() => State] =
        val preparedPairs = anyKeyValues(structure, kvs)
        val showConfig = action match
          case PrintAction =>
            Aggregation.ShowConfig(
              settingValues = true,
              taskValues = true,
              print = println,
              success = false
            )
          case _ => Aggregation.defaultShow(state, showTasks = action == ShowAction)
        Aggregation
          .evaluatingParser(state, showConfig)(preparedPairs)
          .map: evaluate =>
            () =>
              val keyStrings = preparedPairs.map(pp => showKey.show(pp.key)).mkString(", ")
              state.log.debug("Evaluating tasks: " + keyStrings)
              evaluate()
      for
        keys <-
          action match
            case SingleAction => akp
            case ShowAction | PrintAction | MultiAction =>
              for pairs <- rep1sep(akp, token(Space))
              yield pairs.flatten
        keys1 = applyQuery(keys, structure)
        p <-
          if keys.nonEmpty && keys1.isEmpty then emptyResult
          else evaluate(keys1.map(_._1))
      yield p
  end actParser0

  private def applyQuery(
      pairs: Seq[(ScopedKey[?], Option[ProjectQuery])],
      structure: BuildStructure,
  ): Seq[(ScopedKey[?], Option[ProjectQuery])] =
    pairs.filter {
      case (_, None) => true
      case (keys, Some(query)) =>
        val f = query.buildQuery(structure)
        keys.scope.project.toOption match
          case Some(ref: ProjectRef) => f(ref)
          case _                     => true
    }

  private final class ActAction
  private final val ShowAction, MultiAction, SingleAction, PrintAction = new ActAction

  private def actionParser: Parser[ActAction] =
    token(
      ((ShowCommand ^^^ ShowAction) |
        (PrintCommand ^^^ PrintAction) |
        (MultiTaskCommand ^^^ MultiAction)) <~ Space
    ) ?? SingleAction

  def scopedKeyParser(state: State): Parser[ScopedKey[?]] = scopedKeyParser(Project.extract(state))
  def scopedKeyParser(extracted: Extracted): Parser[ScopedKey[?]] =
    scopedKeyParser(extracted.structure, extracted.currentRef)
  def scopedKeyParser(structure: BuildStructure, currentRef: ProjectRef): Parser[ScopedKey[?]] =
    scopedKey(
      structure.index.keyIndex,
      currentRef,
      structure.extra.configurationsForAxis,
      structure.index.keyMap,
      structure.data
    )

  def aggregatedKeyParser(state: State): KeysParser = aggregatedKeyParser(Project.extract(state))
  def aggregatedKeyParser(extracted: Extracted): KeysParser =
    aggregatedKeyParser(extracted.structure, extracted.currentRef)
  def aggregatedKeyParser(structure: BuildStructure, currentRef: ProjectRef): KeysParser =
    scopedKeyAggregated(currentRef, structure.extra.configurationsForAxis, structure)

  private[sbt] def aggregatedKeyParserFilter(extracted: Extracted): KeysParserFilter =
    aggregatedKeyParserFilter(extracted.structure, extracted.currentRef)
  private[sbt] def aggregatedKeyParserFilter(
      structure: BuildStructure,
      currentRef: ProjectRef
  ): KeysParserFilter =
    scopedKeyAggregatedFilter(currentRef, structure.extra.configurationsForAxis, structure)

  def keyValues[T](state: State)(keys: Seq[ScopedKey[T]]): Values[T] =
    keyValues(Project.extract(state))(keys)
  def keyValues[T](extracted: Extracted)(keys: Seq[ScopedKey[T]]): Values[T] =
    keyValues(extracted.structure)(keys)
  def keyValues[T](structure: BuildStructure)(keys: Seq[ScopedKey[T]]): Values[T] =
    keys.flatMap(key => getValue(structure.data, key).map(KeyValue(key, _)))

  private def anyKeyValues(structure: BuildStructure, keys: Seq[ScopedKey[?]]): Seq[KeyValue[?]] =
    keys.flatMap(key => getValue(structure.data, key).map(KeyValue(key, _)))

  private def getValue[T](data: Def.Settings, key: ScopedKey[T]): Option[T] =
    if (java.lang.Boolean.getBoolean("sbt.cli.nodelegation")) data.getDirect(key)
    else data.get(key)

  def requireSession[T](s: State, p: => Parser[T]): Parser[T] =
    if s.get(sessionSettings).isEmpty then failure("No project loaded") else p

  sealed trait ParsedAxis[+T] {
    final def isExplicit = this != Omitted
  }
  object ParsedGlobal extends ParsedAxis[Nothing]
  object ParsedZero extends ParsedAxis[Nothing]
  object Omitted extends ParsedAxis[Nothing]
  final class ParsedValue[T](val value: T) extends ParsedAxis[T]
  def value[T](t: Parser[T]): Parser[ParsedAxis[T]] = t map { v =>
    new ParsedValue(v)
  }
}
