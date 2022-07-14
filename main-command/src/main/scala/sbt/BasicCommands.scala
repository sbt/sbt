/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.nio.file.Paths
import sbt.util.Level
import sbt.internal.util.{ AttributeKey, FullReader, LineReader, Terminal }
import sbt.internal.util.complete.{
  Completion,
  Completions,
  DefaultParsers,
  HistoryCommands,
  Parser,
  TokenCompletions,
  History => CHistory
}
import sbt.internal.util.Types.{ const, idFun }
import sbt.internal.util.Util.{ AnyOps, nil, nilSeq, none }
import sbt.internal.inc.classpath.ClasspathUtil.toLoader
import sbt.internal.inc.ModuleUtilities
import sbt.internal.client.NetworkClient
import DefaultParsers._

import Function.tupled
import Command.applyEffect
import BasicCommandStrings._
import CommandUtil._
import BasicKeys._
import java.io.File

import sbt.io.IO
import sbt.util.Level

import scala.Function.tupled
import scala.collection.mutable.ListBuffer
import scala.util.control.NonFatal

object BasicCommands {
  lazy val allBasicCommands: Seq[Command] = Seq(
    nop,
    ignore,
    help,
    completionsCommand,
    ifLast,
    append,
    setOnFailure,
    clearOnFailure,
    stashOnFailure,
    popOnFailure,
    reboot,
    rebootImpl,
    call,
    early,
    exit,
    shutdown,
    history,
    oldshell,
    client,
    read,
    alias,
    reportResultsCommand,
    mapExecCommand,
    completeExecCommand,
  )

  def nop: Command = Command.custom(s => success(() => s))
  def ignore: Command = Command.command(FailureWall)(idFun)

  def early: Command = Command.arb(earlyParser, earlyHelp)((s, other) => other :: s)

  private[this] def levelParser: Parser[String] =
    Iterator(Level.Debug, Level.Info, Level.Warn, Level.Error) map (l => token(l.toString)) reduce (_ | _)

  private[this] def addPluginSbtFileParser: Parser[File] = {
    token(AddPluginSbtFileCommand) ~> (":" | "=" | Space.map(_.toString)) ~> (StringBasic).examples(
      "/some/extra.sbt"
    ) map {
      new File(_)
    }
  }

  private[this] def addPluginSbtFileStringParser: Parser[String] = {
    token(
      token(AddPluginSbtFileCommand) ~ (":" | "=" | Space.map(_.toString)) ~ (StringBasic)
        .examples("/some/extra.sbt") map {
        case s1 ~ s2 ~ s3 => s1 + s2 + s3
      }
    )
  }

  private[this] def earlyParser: State => Parser[String] = (s: State) => {
    val p1 = token(EarlyCommand + "(") flatMap (_ => otherCommandParser(s) <~ token(")"))
    val p2 = (token("-") | token("--")) flatMap (_ => levelParser)
    val p3 = (token("-") | token("--")) flatMap (_ => addPluginSbtFileStringParser)
    p1 | p2 | p3
  }

  private[this] def earlyHelp = Help(EarlyCommand, EarlyCommandBrief, EarlyCommandDetailed)

  /**
   * Adds additional *.sbt to the plugin build.
   * This must be combined with early command as: --addPluginSbtFile=/tmp/extra.sbt
   */
  def addPluginSbtFile: Command = Command.arb(_ => addPluginSbtFileParser, addPluginSbtFileHelp) {
    (s, extraSbtFile) =>
      val extraFiles = s.get(BasicKeys.extraMetaSbtFiles).toList.flatten
      s.put(BasicKeys.extraMetaSbtFiles, (extraFiles: Seq[File]) :+ extraSbtFile)
  }

  def help: Command = Command.make(HelpCommand, helpBrief, helpDetailed)(helpParser)

  def helpParser(s: State): Parser[() => State] = {
    val h = s.definedCommands.foldLeft(Help.empty)(
      (a, b) =>
        a ++ (try b.help(s)
        catch { case NonFatal(_) => Help.empty })
    )
    val helpCommands = h.detail.keySet
    val spacedArg = singleArgument(helpCommands).?
    applyEffect(spacedArg)(runHelp(s, h))
  }

  def runHelp(s: State, h: Help)(arg: Option[String]): State = {

    val (extraArgs, remainingCommands) = s.remainingCommands match {
      case xs :+ exec if exec.commandLine == "shell" => (xs, exec :: Nil)
      case xs                                        => (xs, nil[Exec])
    }

    val topic = (arg.toList ++ extraArgs.map(_.commandLine)) match {
      case Nil => none[String]
      case xs  => xs.mkString(" ").some
    }
    val message = try Help.message(h, topic)
    catch { case NonFatal(ex) => ex.toString }
    System.out.println(message)
    s.copy(remainingCommands = remainingCommands)
  }

  def completionsCommand: Command =
    Command(CompletionsCommand, CompletionsBrief, CompletionsDetailed)(_ => completionsParser)(
      runCompletions(_)(_)
    )

  @deprecated("No longer public", "1.1.1")
  def completionsParser(state: State): Parser[String] = completionsParser

  private[this] def completionsParser: Parser[String] = {
    val notQuoted = (NotQuoted ~ any.*) map { case (nq, s) => nq + s }
    val quotedOrUnquotedSingleArgument = Space ~> (StringVerbatim | StringEscapable | notQuoted)
    token((quotedOrUnquotedSingleArgument ?? "").examples("", " "))
  }

  def runCompletions(state: State)(input: String): State = {
    Parser.completions(state.combinedParser, input, 9).get map { c =>
      if (c.isEmpty) input else input + c.append
    } foreach { c =>
      System.out.println("[completions] " + c.replaceAll("\n", " "))
    }
    state
  }

  private[sbt] def multiParserImpl(state: Option[State]): Parser[List[String]] =
    multiParserImpl(state, "alias" :: Nil)
  private[sbt] def multiParserImpl(
      state: Option[State],
      exclude: Seq[String]
  ): Parser[List[String]] = {
    val nonSemi = charClass(_ != ';', "not ';'")
    val nonDelim = charClass(c => c != '"' && c != '{' && c != '}', label = "not '\"', '{', '}'")
    // Accept empty commands to simplify the parser.
    val cmdPart =
      matched(((nonSemi & nonDelim).map(_.toString) | StringEscapable | braces('{', '}')).*)
        .examples()

    val completionParser: Option[Parser[String]] =
      state.map(s => (matched(s.nonMultiParser) & cmdPart) | cmdPart)
    val cmdParser = {
      val parser = completionParser.getOrElse(cmdPart).map(_.trim)
      exclude.foldLeft(parser) { case (p, e) => p & not(OptSpace ~ s"$e ", s"!$e").examples() }
    }
    val multiCmdParser: Parser[String] = token(';') ~> OptSpace ~> cmdParser

    /*
     * We accept empty commands at the end of the the list as an implementation detail that allows
     * for a trailing semi-colon without an extra parser since the cmdParser accepts an empty string
     * and the multi parser is `token(';') ~ cmdParser`. We do not want to accept empty commands
     * that occur in the middle of the sequence so if  we find one, we return a failed parser. If
     * we wanted to relax that restriction, then we could just replace the flatMap below with
     * `rest.filterNot(_.isEmpty)`.
     */
    def validateCommands(s: Seq[String]): Parser[List[String]] = {
      val result = new ListBuffer[String]
      val it = s.iterator
      var fail = false
      while (it.hasNext && !fail) {
        it.next() match {
          case ""   => fail = it.hasNext; ()
          case next => result += next; ()
        }
      }
      if (fail) Parser.failure(s"Couldn't parse empty commands in ${s.mkString(";")}")
      else Parser.success(result.toList)
    }

    (cmdParser ~ multiCmdParser.+)
      .flatMap {
        case ("", rest) => validateCommands(rest)
        case (p, rest)  => validateCommands(rest).map(p :: _)
      }
  }

  def multiParser(s: State): Parser[List[String]] = multiParserImpl(Some(s))

  def multiApplied(state: State): Parser[() => State] = {
    Command.applyEffect(multiParserImpl(Some(state))) {
      // the (@ _ :: _) ensures tail length >= 1.
      case commands @ first :: (tail @ _ :: _) =>
        require(first.head != ' ', s"Commands must be trimmed. Received: '$first'.")
        // Note: scalafmt refuses to align on '*' using multiline /*...*/ here
        //
        // This case is only executed if the multi parser actually returns multiple commands to run.
        // Otherwise we just prefix the single extracted command with the semicolon stripped to the
        // state. Since there is no semicolon in the stripped command, the multi command parser will
        // fail to parse that single command so we do not end up in a loop.
        //
        // If there are multiple commands, we give a named command a chance to parse the raw input
        // and possibly directly evaluate the side effects. This is desirable if, for example,
        // the command runs other commands. The continuous (~) command is one such example. If the
        // input command is `~foo; bar`, the multi parser would extract "~foo" :: "bar" :: Nil.
        // If we naively just prepended the state with both of these commands, it would run '~foo'
        // and then when watch exited, it'd run 'bar', which is likely unexpected. To address this,
        // we search for a named command whose name is a prefix of the first command in the list.
        // In this case, we'd find '~' and then pass 'foo;bar' into its parser. If this succeeds,
        // which it will in the case of continuous (so long as foo and bar are valid commands),
        // then we directly evaluate the `() => State` returned by the parser. Otherwise, we
        // fall back to prefixing the multi commands to the state.
        //
        state.nonMultiCommands.view.flatMap { command =>
          command.nameOption match {
            case Some(commandName) if first.startsWith(commandName) =>
              // A lot of commands expect leading semicolons in their parsers. In order to
              // ensure that they are multi-command capable, we strip off any leading spaces.
              // Without doing this, we could run simple commands like `set` with the full
              // input. This would likely fail because `set` doesn't know how to handle
              // semicolons. This is a bit of a hack that is specifically there
              // to handle `~` which doesn't require a space before its argument. Any command
              // whose parser accepts multi commands without a leading space should be accepted.
              // All other commands should be rejected. Note that `alias` is also handled by
              // this case.
              val commandArgs =
                (first.drop(commandName.length).trim :: Nil ::: tail).mkString(";")
              parse(commandArgs, command.parser(state)).toOption
            case _ => none[() => State]
          }
        }.headOption match {
          case Some(s) => s()
          case _       => commands ::: state
        }
      case commands => commands ::: state
    }
  }

  val multi: Command =
    Command.custom(multiApplied, Help(Multi, MultiBrief, MultiDetailed), Multi)

  lazy val otherCommandParser: State => Parser[String] =
    (s: State) => token(OptSpace ~> combinedLax(s, NotSpaceClass ~ any.*))

  def combinedLax(s: State, any: Parser[_]): Parser[String] =
    matched((s.combinedParser: Parser[_]) | token(any, hide = const(true)))

  def ifLast: Command =
    Command(IfLast, Help.more(IfLast, IfLastDetailed))(otherCommandParser)(
      (s, arg) => if (s.remainingCommands.isEmpty) arg :: s else s
    )

  def append: Command =
    Command(AppendCommand, Help.more(AppendCommand, AppendLastDetailed))(otherCommandParser)(
      (s, arg) => s.copy(remainingCommands = s.remainingCommands :+ Exec(arg, s.source))
    )

  def setOnFailure: Command =
    Command(OnFailure, Help.more(OnFailure, OnFailureDetailed))(otherCommandParser)(
      (s, arg) => s.copy(onFailure = Some(Exec(arg, s.source)))
    )

  def clearOnFailure: Command = Command.command(ClearOnFailure)(s => s.copy(onFailure = None))

  def stashOnFailure: Command =
    Command.command(StashOnFailure)(
      s => s.copy(onFailure = None).update(OnFailureStack)(s.onFailure :: _.toList.flatten)
    )

  def popOnFailure: Command = Command.command(PopOnFailure) { s =>
    val stack = s.get(OnFailureStack).getOrElse(nil)
    val updated =
      if (stack.isEmpty) s.remove(OnFailureStack) else s.put(OnFailureStack, stack.tail)
    updated.copy(onFailure = stack.headOption.flatten)
  }

  def reboot: Command =
    Command(RebootCommand, Help.more(RebootCommand, RebootDetailed))(_ => rebootOptionParser) {
      case (s, (full, currentOnly)) =>
        val option = if (full) " full" else if (currentOnly) " dev" else ""
        RebootNetwork :: s"$RebootImpl$option" :: s
    }
  def rebootImpl: Command =
    Command.arb(_ => (RebootImpl ~> rebootOptionParser).examples()) {
      case (s, (full, currentOnly)) =>
        s.reboot(full, currentOnly)
    }

  @deprecated("Use rebootOptionParser", "1.1.0")
  def rebootParser(s: State): Parser[Boolean] = rebootOptionParser map { case (full, _) => full }

  private[sbt] def rebootOptionParser: Parser[(Boolean, Boolean)] = {
    val fullOption = "full" ^^^ ((true, false))
    val devOption = "dev" ^^^ ((false, true))
    token(Space ~> (fullOption | devOption)) ?? ((false, false))
  }

  def call: Command =
    Command(ApplyCommand, Help.more(ApplyCommand, ApplyDetailed))(_ => callParser) {
      case (state, (cp, args)) =>
        val parentLoader = getClass.getClassLoader
        def argsStr = args mkString ", "
        def cpStr = cp mkString File.pathSeparator
        def fromCpStr = if (cp.isEmpty) "" else s" from $cpStr"
        state.log info s"Applying State transformations $argsStr$fromCpStr"
        val loader =
          if (cp.isEmpty) parentLoader else toLoader(cp.map(f => Paths.get(f)), parentLoader)
        val loaded =
          args.map(arg => ModuleUtilities.getObject(arg, loader).asInstanceOf[State => State])
        loaded.foldLeft(state)((s, obj) => obj(s))
    }

  def callParser: Parser[(Seq[String], Seq[String])] =
    token(Space) ~> ((classpathOptionParser ?? nilSeq) ~ rep1sep(className, token(Space)))

  private[this] def className: Parser[String] = {
    val base = StringBasic & not('-' ~> any.*, "Class name cannot start with '-'.")
    def single(s: String) = Completions.single(Completion.displayOnly(s))
    val compl = TokenCompletions.fixed(
      (seen, _) => if (seen.startsWith("-")) Completions.nil else single("<class name>")
    )
    token(base, compl)
  }

  private[this] def classpathOptionParser: Parser[Seq[String]] =
    token(("-cp" | "-classpath") ~> Space) ~> classpathStrings <~ token(Space)

  private[this] def classpathStrings: Parser[Seq[String]] =
    token(StringBasic.map(s => IO.pathSplit(s).toSeq), "<classpath>")

  def exit: Command = Command.command(TerminateAction, exitBrief, exitBrief) { s =>
    s.source match {
      case Some(c) if c.channelName.startsWith("network") =>
        s"${DisconnectNetworkChannel} ${c.channelName}" :: s
      case _ => s exit true
    }
  }
  def shutdown: Command = Command.command(Shutdown, shutdownBrief, shutdownBrief) { s =>
    s.source match {
      case Some(c) if c.channelName.startsWith("network") =>
        s"${DisconnectNetworkChannel} ${c.channelName}" :: (Exec(Shutdown, None) +: s)
      case _ => s exit true
    }
  }

  @deprecated("Replaced by BuiltInCommands.continuous", "1.3.0")
  def continuous: Command =
    Command(ContinuousExecutePrefix, continuousBriefHelp, continuousDetail)(otherCommandParser) {
      (s, arg) =>
        withAttribute(s, Watched.Configuration, "Continuous execution not configured.") { w =>
          val repeat = ContinuousExecutePrefix + (if (arg.startsWith(" ")) arg else " " + arg)
          Watched.executeContinuously(w, s, arg, repeat)
        }
    }

  def history: Command = Command.custom(historyParser, BasicCommandStrings.historyHelp)

  def historyParser(s: State): Parser[() => State] =
    Command.applyEffect(HistoryCommands.actionParser) { histFun =>
      val hp = (s get historyPath).flatten
      val lines = hp.toList.flatMap(p => IO.readLines(p)).toIndexedSeq
      histFun(CHistory(lines, hp)) match {
        case Some(commands) =>
          commands foreach println //printing is more appropriate than logging
          (commands ::: s).continue
        case None => s.fail
      }
    }

  def oldshell: Command = Command.command(OldShell, Help.more(Shell, OldShellDetailed)) { s =>
    val history = (s get historyPath) getOrElse (new File(s.baseDir, ".history")).some
    val prompt = (s get shellPrompt) match { case Some(pf) => pf(s); case None => "> " }
    val reader = new FullReader(history, s.combinedParser, LineReader.HandleCONT, Terminal.console)
    val line = reader.readLine(prompt)
    line match {
      case Some(line) =>
        val newState = s
          .copy(
            onFailure = Some(Exec(OldShell, None)),
            remainingCommands = Exec(line, s.source) +: Exec(OldShell, None) +: s.remainingCommands
          )
          .setInteractive(true)
        if (line.trim.isEmpty) newState else newState.clearGlobalLog
      case None => s.setInteractive(false)
    }
  }

  def client: Command =
    Command(Client, Help.more(Client, ClientDetailed))(_ => clientParser)(runClient)

  def clientParser: Parser[Seq[String]] =
    (token(Space) ~> repsep(StringBasic, token(Space))) | (token(EOF) map (_ => nilSeq))

  def runClient(s0: State, inputArg: Seq[String]): State = {
    val arguments = inputArg.toList ++
      (s0.remainingCommands match {
        case e :: Nil if e.commandLine == "shell" => nil
        case xs                                   => xs map (_.commandLine)
      })
    NetworkClient.run(s0.configuration, arguments)
    TerminateAction :: s0.copy(remainingCommands = Nil)
  }

  def read: Command =
    Command(ReadCommand, Help.more(ReadCommand, ReadDetailed))(readParser)(doRead(_)(_))

  def readParser(s: State): Parser[Either[Int, Seq[File]]] = {
    val files = (token(Space) ~> fileParser(s.baseDir)).+
    val portAndSuccess = token(OptSpace) ~> Port
    portAndSuccess || files
  }

  def doRead(s: State)(arg: Either[Int, Seq[File]]): State =
    arg match {
      case Left(portAndSuccess) =>
        val port = math.abs(portAndSuccess)
        val previousSuccess = portAndSuccess >= 0
        readMessage(port, previousSuccess) match {
          case Some(message) =>
            (message :: (ReadCommand + " " + port) :: s).copy(
              onFailure = Some(Exec(ReadCommand + " " + (-port), s.source))
            )
          case None =>
            System.err.println("Connection closed.")
            s.fail
        }
      case Right(from) =>
        val notFound = notReadable(from)
        if (notFound.isEmpty)
          // this means that all commands from all files are loaded, parsed, & inserted before any are executed
          readLines(from).toList ::: s
        else {
          s.log.error("Command file(s) not readable: \n\t" + notFound.mkString("\n\t"))
          s
        }
    }

  private def readMessage(port: Int, previousSuccess: Boolean): Option[String] = {
    // split into two connections because this first connection ends the previous communication
    xsbt.IPC.client(port) { _.send(previousSuccess.toString) }
    //   and this second connection starts the next communication
    xsbt.IPC.client(port) { ipc =>
      val message = ipc.receive
      if (message eq null) None else Some(message)
    }
  }

  def alias: Command =
    Command(AliasCommand, Help.more(AliasCommand, AliasDetailed)) { s =>
      val name = token(OpOrID.examples(aliasNames(s): _*))
      val assign = token(OptSpace ~ '=' ~ OptSpace)
      val sfree = removeAliases(s)
      val to = matched(sfree.combinedParser, partial = true).failOnException | any.+.string
      OptSpace ~> (name ~ (assign ~> to.?).?).?
    }(runAlias)

  def runAlias(s: State, args: Option[(String, Option[Option[String]])]): State =
    args match {
      case None =>
        printAliases(s); s
      case Some(x ~ None) if !x.isEmpty =>
        printAlias(s, x.trim); s
      case Some(name ~ Some(None))        => removeAlias(s, name.trim)
      case Some(name ~ Some(Some(value))) => addAlias(s, name.trim, value.trim)
    }
  def addAlias(s: State, name: String, value: String): State =
    if (Command validID name) {
      val removed = removeAlias(s, name)
      if (value.isEmpty) removed else addAlias0(removed, name, value)
    } else {
      System.err.println("Invalid alias name '" + name + "'.")
      s.fail
    }
  private[this] def addAlias0(s: State, name: String, value: String): State =
    s.copy(definedCommands = newAlias(name, value) +: s.definedCommands)

  def removeAliases(s: State): State = removeTagged(s, CommandAliasKey)

  def removeAlias(s: State, name: String): State =
    s.copy(definedCommands = s.definedCommands.filter(c => !isAliasNamed(name, c)))

  def removeTagged(s: State, tag: AttributeKey[_]): State =
    s.copy(definedCommands = removeTagged(s.definedCommands, tag))

  def removeTagged(as: Seq[Command], tag: AttributeKey[_]): Seq[Command] =
    as.filter(c => !(c.tags contains tag))

  def isAliasNamed(name: String, c: Command): Boolean = isNamed(name, getAlias(c))

  def isNamed(name: String, alias: Option[(String, String)]): Boolean =
    alias match { case None => false; case Some((n, _)) => name == n }

  def getAlias(c: Command): Option[(String, String)] = c.tags get CommandAliasKey
  def printAlias(s: State, name: String): Unit = printAliases(aliases(s, (n, _) => n == name))
  def printAliases(s: State): Unit = printAliases(allAliases(s))

  def printAliases(as: Seq[(String, String)]): Unit =
    for ((name, value) <- as)
      println("\t" + name + " = " + value)

  def aliasNames(s: State): Seq[String] = allAliases(s).map(_._1)
  def allAliases(s: State): Seq[(String, String)] = aliases(s, (_, _) => true)
  def aliases(s: State, pred: (String, String) => Boolean): Seq[(String, String)] =
    s.definedCommands.flatMap(c => getAlias(c).filter(tupled(pred)))

  def newAlias(name: String, value: String): Command =
    Command
      .make(name, (name, s"'$value'"), s"Alias of '$value'")(aliasBody(name, value))
      .tag(CommandAliasKey, (name, value))

  def aliasBody(name: String, value: String)(state: State): Parser[() => State] = {
    val aliasRemoved = removeAlias(state, name)
    // apply the alias value to the commands of `state` except for the alias to avoid recursion (#933)
    val partiallyApplied = Parser(aliasRemoved.combinedParser)(value)
    val arg: Parser[String] = matched(
      partiallyApplied & (success((): Any) | ((SpaceClass ~ any.*): Parser[Any]))
    )
    // by scheduling the expanded alias instead of directly executing,
    // we get errors on the expanded string (#598)
    arg.map(str => () => (value + str) :: state)
  }

  def delegateToAlias(name: String, orElse: Parser[() => State])(
      state: State
  ): Parser[() => State] =
    aliases(state, (nme, _) => nme == name).headOption match {
      case None         => orElse
      case Some((n, v)) => aliasBody(n, v)(state)
    }

  val CommandAliasKey: AttributeKey[(String, String)] =
    AttributeKey[(String, String)](
      "is-command-alias",
      "Internal: marker for Commands created as aliases for another command."
    )

  private[sbt] def reportParser(key: String) =
    (key: Parser[String]).examples() ~> " ".examples() ~> matched(any.*).examples()
  def reportResultsCommand =
    Command.arb(_ => reportParser(ReportResult)) { (state, id) =>
      val newState = state.get(execMap) match {
        case Some(m) => state.put(execMap, m - id)
        case _       => state
      }
      newState.get(execResults) match {
        case Some(m) if m.contains(id) => state.put(execResults, m - id)
        case _                         => state.fail
      }
    }
  def mapExecCommand =
    Command.arb(_ => reportParser(MapExec)) { (state, mapping) =>
      mapping.split(" ") match {
        case Array(key, value) =>
          state.get(execMap) match {
            case Some(m) => state.put(execMap, m + (key -> value))
            case None    => state.put(execMap, Map(key -> value))
          }
        case _ => state
      }
    }
  def completeExecCommand =
    Command.arb(_ => reportParser(CompleteExec)) { (state, id) =>
      val newState = state.get(execResults) match {
        case Some(m) => state.put(execResults, m + (id -> true))
        case _       => state.put(execResults, Map(id -> true))
      }
      newState.get(execMap) match {
        case Some(m) => newState.put(execMap, m - id)
        case _       => newState
      }
    }
  private[sbt] val execResults = AttributeKey[Map[String, Boolean]]("execResults", Int.MaxValue)
  private[sbt] val execMap = AttributeKey[Map[String, String]]("execMap", Int.MaxValue)
}
