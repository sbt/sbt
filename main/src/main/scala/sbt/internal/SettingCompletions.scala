/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import sbt.internal.util.{ AttributeKey, complete, Relation, Util }
import sbt.util.Show
import sbt.librarymanagement.Configuration

import ProjectExtra.{ relation }
import Def.{ ScopedKey, Setting }
import Scope.Global
import sbt.SlashSyntax0.given
import complete._
import DefaultParsers._

/**
 * The resulting `session` and verbose and quiet summaries of the result of a set operation.
 * The verbose summary will typically use more vertical space and show full details,
 * while the quiet summary will be a couple of lines and truncate information.
 */
private[sbt] class SetResult(
    val session: SessionSettings,
    val verboseSummary: String,
    val quietSummary: String
)

/** Defines methods for implementing the `set` command. */
private[sbt] object SettingCompletions {

  /**
   * Implementation of the `set every` command.  Each setting in the provided `settings` sequence will be applied in all scopes,
   * overriding all previous definitions of the underlying AttributeKey.
   * The settings injected by this method cannot be later persisted by the `session save` command.
   */
  def setAll(extracted: Extracted, settings: Seq[Setting[?]]): SetResult = {
    import extracted._
    val r = Project.relation(extracted.structure, true)
    val allDefs = Def
      .flattenLocals(
        Def.compiled(extracted.structure.settings, true)(using
          structure.delegates,
          structure.scopeLocal,
          implicitly[Show[ScopedKey[?]]],
        )
      )
      .keys
    val projectScope = Load.projectScope(currentRef)
    def resolve(s: Setting[?]): Seq[Setting[?]] =
      Load.transformSettings(projectScope, currentRef.build, rootProject, s :: Nil)

    def rescope[T](setting: Setting[T]): Seq[Setting[?]] = {
      val akey = setting.key.key
      val global = ScopedKey(Global, akey)
      val globalSetting = resolve(Def.setting(global, setting.init, setting.pos))
      globalSetting ++ allDefs.flatMap { d =>
        if d.key == akey then Seq((d.scope / SettingKey(akey)) := global.value)
        else Nil
      }
    }
    val redefined = settings.flatMap(x => rescope(x))
    val session = extracted.session.appendRaw(redefined)
    setResult(session, r, redefined)
  }

  /**
   * Implementation of the `set` command that will reload the current project with `settings`
   *  appended to the current settings.
   */
  def setThis(extracted: Extracted, settings: Seq[Def.Setting[?]], arg: String): SetResult = {
    import extracted._
    val append =
      Load.transformSettings(Load.projectScope(currentRef), currentRef.build, rootProject, settings)
    val newSession = session.appendSettings(append map (a => (a, arg.split('\n').toList)))
    val r = Project.relation(newSession.mergeSettings, true)(using
      structure.delegates,
      structure.scopeLocal,
      summon[Show[ScopedKey[?]]],
    )
    setResult(newSession, r, append)
  }

  private def setResult(
      session: SessionSettings,
      r: Relation[ScopedKey[?], ScopedKey[?]],
      redefined: Seq[Setting[?]],
  )(using show: Show[ScopedKey[?]]): SetResult = {
    val redefinedKeys = redefined.map(_.key).toSet
    val affectedKeys = redefinedKeys.flatMap(r.reverse)
    def summary(verbose: Boolean): String = setSummary(redefinedKeys, affectedKeys, verbose)
    new SetResult(session, summary(true), summary(false))
  }

  private def setSummary(
      redefined: Set[ScopedKey[?]],
      affected: Set[ScopedKey[?]],
      verbose: Boolean,
  )(using display: Show[ScopedKey[?]]): String = {
    val QuietLimit = 3
    def strings(in: Set[ScopedKey[?]]): Seq[String] = in.toSeq.map(sk => display.show(sk)).sorted
    def lines(in: Seq[String]): (String, Boolean) =
      if (in.isEmpty)
        ("no settings or tasks.", false)
      else if (verbose)
        (in.mkString("\n\t", "\n\t", "\n"), false)
      else
        quietList(in)
    def quietList(in: Seq[String]): (String, Boolean) = {
      val (first, last) = in.splitAt(QuietLimit)
      if (last.isEmpty) (first.mkString(", "), false)
      else {
        val s = first.take(QuietLimit - 1).mkString("", ", ", " and " + last.size + " others.")
        (s, true)
      }
    }
    if (redefined.isEmpty) "No settings or tasks were redefined."
    else {
      val (redef, trimR) = lines(strings(redefined))
      val (used, trimU) = lines(strings(affected))
      val details = if (trimR || trimU) "\n\tRun `last` for details." else ""
      val valuesString = if (redefined.size == 1) "value" else "values"
      "Defining %s\nThe new %s will be used by %s%s".format(redef, valuesString, used, details)
    }
  }

  /**
   * Parser that provides tab completion for the main argument to the `set` command.
   * `settings` are the evaluated settings for the build, `rawKeyMap` maps the hyphenated key identifier to the key object,
   * and `context` is the current project.
   * The tab completion will try to present the most relevant information first, with additional descriptions or keys available
   * when there are fewer choices or tab is pressed multiple times.
   * The last part of the completion will generate a template for the value or function literal that will initialize the setting or task.
   */
  def settingParser(
      settings: Def.Settings,
      rawKeyMap: Map[String, AttributeKey[?]],
      context: ResolvedProject,
  ): Parser[String] = {
    val keyMap: Map[String, AttributeKey[?]] =
      rawKeyMap.map { case (k, v) => (keyScalaID(k), v) }.toMap
    val full = for {
      defineKey <- scopedKeyParser(keyMap, settings, context)
      a <- assign(defineKey)
      _ <- valueParser(defineKey, a)
    } yield () // parser is currently only for completion and the parsed data structures are not used

    matched(full) | any.+.string
  }

  /** Parser for a Scope+AttributeKey (ScopedKey). */
  def scopedKeyParser(
      keyMap: Map[String, AttributeKey[?]],
      settings: Def.Settings,
      context: ResolvedProject
  ): Parser[ScopedKey[?]] = {
    val cutoff = KeyRanks.MainCutoff
    val keyCompletions = fixedCompletions { (seen, level) =>
      completeKey(seen, keyMap, level, cutoff, 10).toSet
    }
    val keyID: Parser[AttributeKey[?]] = scalaID(keyMap, "key")
    val keyParser = token(keyID, keyCompletions)
    for (key <- keyParser; scope <- scopeParser(key, settings, context))
      yield ScopedKey(scope, key)
  }

  /** Parser for the `in` method name that slightly augments the naive completion to give a hint of the purpose of `in`. */
  val inParser = tokenDisplay(Space ~> InMethod, "%s <scope>".format(InMethod))

  /**
   * Parser for the initialization expression for the assignment method `assign` on the key `sk`.
   * `scopedKeyP` is used to parse and complete the input keys for an initialization that depends on other keys.
   */
  def valueParser(sk: ScopedKey[?], assign: Assign.Value): Parser[Seq[ScopedKey[?]]] = {
    val fullTypeString = keyTypeString(sk.key)
    val typeString = if (assignNoAppend(assign)) fullTypeString else "..."
    if (assign == Assign.Update) {
      val function = "{(prev: " + typeString + ") => /*" + typeString + "*/ }"
      token(OptSpace ~ function) ^^^ Nil
    } else {
      val value = "/* value of type " + typeString + " */"
      token(Space ~ value) ^^^ Nil
    }
  }

  /**
   * Parser for a Scope for a `key` given the current project `context` and evaluated `settings`.
   * The completions are restricted to be more useful.  Currently, this parser will suggest
   * only known axis values for configurations and tasks and only in that order.
   */
  def scopeParser(
      key: AttributeKey[?],
      settings: Def.Settings,
      context: ResolvedProject
  ): Parser[Scope] = {
    val definedScopes = settings.keys.collect { case sk if sk.key == key => sk.scope }
    scope(settings.scopes.toSeq, definedScopes.toSeq, context)
  }

  private def scope(
      allScopes: Seq[Scope],
      definedScopes: Seq[Scope],
      context: ResolvedProject,
  ): Parser[Scope] = {
    def axisParser[T](
        axis: Scope => ScopeAxis[T],
        name: T => String,
        description: T => Option[String],
        label: String,
    ): Parser[ScopeAxis[T]] = {
      def getChoice(s: Scope): Seq[(String, T)] = axis(s) match {
        case Select(t) => (name(t), t) :: Nil
        case _         => Nil
      }
      def getChoices(scopes: Seq[Scope]): Map[String, T] = scopes.flatMap(getChoice).toMap
      val definedChoices: Set[String] =
        definedScopes.flatMap(s => axis(s).toOption.map(name)).toSet
      val fullChoices: Map[String, T] = getChoices(allScopes)
      val completions = fixedCompletions { (seen, level) =>
        completeScope(seen, level, definedChoices, fullChoices)(description).toSet
      }
      Act.optionalAxis(
        inParser ~> token(Space) ~> token(scalaID(fullChoices, label), completions),
        This,
      )
    }
    val configurations: Map[String, Configuration] =
      context.configurations.map(c => (configScalaID(c.name), c)).toMap
    val configParser = axisParser[ConfigKey](
      _.config,
      c => configScalaID(c.name),
      ck => configurations.get(ck.name).map(_.description),
      "configuration",
    )
    val taskParser =
      axisParser[AttributeKey[?]](_.task, k => keyScalaID(k.label), _.description, "task")
    val nonGlobal = (configParser ~ taskParser) map { case (c, t) => Scope(This, c, t, Zero) }
    val global = inParser ~> token((Space ~ GlobalID) ^^^ Global)
    global | nonGlobal
  }

  /** Parser for the assignment method (such as `:=`) for defining `key`. */
  def assign(key: ScopedKey[?]): Parser[Assign.Value] = {
    val completions = fixedCompletions { (seen, _) =>
      completeAssign(seen, key).toSet
    }
    val identifier =
      Act
        .filterStrings(
          Op,
          Assign.values.map(_.toString),
          "assignment method"
        )
        .map(Assign.withName)
    token(Space) ~> token(optionallyQuoted(identifier), completions)
  }

  private def fixedCompletions(f: (String, Int) => Set[Completion]): TokenCompletions =
    TokenCompletions.fixed((s, l) => Completions(f(s, l)))

  private def scalaID[T](keyMap: Map[String, T], label: String): Parser[T] = {
    val identifier = Act
      .filterStrings(ScalaID, keyMap.keySet, label)
      .map(keyMap)
    optionallyQuoted(identifier)
  }

  /** Produce a new parser that allows the input accepted by `p` to be quoted in backticks. */
  def optionallyQuoted[T](p: Parser[T]): Parser[T] =
    (Backtick.? ~ p) flatMap { case (quote, id) =>
      if (quote.isDefined) Backtick.? ^^^ id else success(id)
    }

  /**
   * Completions for an assignment method for `key` given the tab completion `level` and existing partial string `seen`.
   * This will filter possible assignment methods based on the underlying type of `key`, so that only `<<=` is shown for input tasks, for example.
   */
  def completeAssign(seen: String, key: ScopedKey[?]): Seq[Completion] = {
    val allowed: Iterable[Assign.Value] =
      if (appendable(key.key)) Assign.values
      else assignNoAppend
    val applicable = allowed.toSeq.flatMap { a =>
      val s = a.toString
      if s.startsWith(seen) then (s, a) :: Nil else Nil
    }
    completeDescribed(seen, true, applicable)(assignDescription)
  }

  def completeKey(
      seen: String,
      keys: Map[String, AttributeKey[?]],
      level: Int,
      prominentCutoff: Int,
      detailLimit: Int
  ): Seq[Completion] =
    completeSelectDescribed(seen, level, keys, detailLimit)(_.description) { case (_, v) =>
      v.rank <= prominentCutoff
    }

  def completeScope[T](
      seen: String,
      level: Int,
      definedChoices: Set[String],
      allChoices: Map[String, T]
  )(description: T => Option[String]): Seq[Completion] =
    completeSelectDescribed(seen, level, allChoices, 10)(description) { case (k, _) =>
      definedChoices(k)
    }

  def completeSelectDescribed[T](seen: String, level: Int, all: Map[String, T], detailLimit: Int)(
      description: T => Option[String]
  )(prominent: (String, T) => Boolean): Seq[Completion] = {
    val applicable = all.toSeq.filter { case (k, _) => k.startsWith(seen) }
    val prominentOnly = applicable filter { case (k, v) => prominent(k, v) }

    val showAll = (level >= 3) || (level == 2 && prominentOnly.lengthCompare(
      detailLimit
    ) <= 0) || prominentOnly.isEmpty
    val showKeys = if (showAll) applicable else prominentOnly
    val showDescriptions = (level >= 2) || showKeys.lengthCompare(detailLimit) <= 0
    completeDescribed(seen, showDescriptions, showKeys)(s => description(s).toList.mkString)
  }
  def completeDescribed[T](seen: String, showDescriptions: Boolean, in: Seq[(String, T)])(
      description: T => String
  ): Seq[Completion] = {
    def appendString(id: String): String = id.stripPrefix(seen) + " "
    if (in.isEmpty) Nil
    else if (showDescriptions) {
      val withDescriptions = in map { case (id, key) => (id, description(key)) }
      val padded = CommandUtil.aligned("", "   ", withDescriptions)
      padded.zip(in).map { case (line, (id, _)) =>
        Completion.tokenDisplay(append = appendString(id), display = line + "\n")
      }
    } else
      in map { case (id, _) => Completion.tokenDisplay(display = id, append = appendString(id)) }
  }

  /**
   * Transforms the hyphenated key label `k` into camel-case and quotes it with backticks if it is a Scala keyword.
   * This is intended to be an estimate of the Scala identifier that may be used to reference the keyword in the default sbt context.
   */
  def keyScalaID(k: String): String = Util.quoteIfKeyword(Util.hyphenToCamel(k))

  /**
   * Transforms the configuration name `c` so that the first letter is capitalized and the name is quoted with backticks if it is a Scala keyword.
   * This is intended to be an estimate of the Scala identifier that may be used to reference the keyword in the default sbt context.
   */
  def configScalaID(c: String): String = Util.quoteIfKeyword(c.capitalize)

  /**
   * Returns a string representation of the underlying type T for a `key` representing a `Setting[T]`, `Task[T]`, or `InputTask[T]`.
   * This string representation is currently a cleaned up toString of the underlying ClassTag.
   */
  def keyTypeString[T](key: AttributeKey[?]): String =
    complete.TypeString.cleanup(key.tag.typeArg.toString)

  /** True if the `key` represents a setting or task that may be appended using an assignment method such as `+=`. */
  def appendable(key: AttributeKey[?]): Boolean =
    appendableClasses.exists(_.isAssignableFrom(key.tag.typeArg))

  /** The simple name of the Global scope, which can be used to reference it in the default setting context. */
  final val GlobalID = Scope.Global.getClass.getSimpleName.stripSuffix("$")

  /** Character used to quote a Scala identifier that would otherwise be interpreted as a keyword. */
  final val Backtick = '`'

  /** Name of the method that modifies the scope of a key. */
  final val InMethod = "in"

  /** Assignment methods that may be called on a setting or task. */
  object Assign extends Enumeration {
    val AppendValue = Value("+=")
    val AppendValues = Value("++=")
    val Define = Value(":=")
    val Update = Value("~=")
  }
  import Assign._

  /** Returns the description associated with the provided assignment method. */
  def assignDescription(a: Assign.Value): String = a match {
    case AppendValue  => "append value"
    case AppendValues => "append values"
    case Define       => "define value, overwriting any existing value"
    case Update       => "transform existing value"
  }

  /** The assignment methods except for the ones that append. */
  val assignNoAppend: Set[Assign.Value] = Set(Define, Update)

  /** Class values to approximate which types can be appended */
  val appendableClasses = Seq(
    classOf[Seq[?]],
    classOf[Map[?, ?]],
    classOf[Set[?]],
    classOf[Int],
    classOf[Double],
    classOf[Long],
    classOf[String]
  )
}
