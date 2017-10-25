/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import sbt.util.Level
import sbt.internal.util.{ AttributeKey, FullReader }
import sbt.internal.util.complete.{
  Completion,
  Completions,
  DefaultParsers,
  History => CHistory,
  HistoryCommands,
  Parser,
  TokenCompletions
}
import sbt.internal.util.Types.{ const, idFun }
import sbt.internal.inc.classpath.ClasspathUtilities.toLoader
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
import scala.util.control.NonFatal

object BasicCommands {
  lazy val allBasicCommands: Seq[Command] = Seq(
    nop,
    ignore,
    help,
    completionsCommand,
    multi,
    ifLast,
    append,
    setOnFailure,
    clearOnFailure,
    stashOnFailure,
    popOnFailure,
    reboot,
    call,
    early,
    exit,
    continuous,
    history,
    oldshell,
    client,
    read,
    alias
  ) ++ compatCommands

  def nop: Command = Command.custom(s => success(() => s))
  def ignore: Command = Command.command(FailureWall)(idFun)

  def early: Command = Command.arb(earlyParser, earlyHelp)((s, other) => other :: s)

  private[this] def levelParser: Parser[String] =
    Iterator(Level.Debug, Level.Info, Level.Warn, Level.Error) map (l => token(l.toString)) reduce (_ | _)

  private[this] def earlyParser: State => Parser[String] = (s: State) => {
    val p1 = token(EarlyCommand + "(") flatMap (_ => otherCommandParser(s) <~ token(")"))
    val p2 = token("-") flatMap (_ => levelParser)
    p1 | p2
  }

  private[this] def earlyHelp = Help(EarlyCommand, EarlyCommandBrief, EarlyCommandDetailed)

  def help: Command = Command.make(HelpCommand, helpBrief, helpDetailed)(helpParser)

  def helpParser(s: State): Parser[() => State] = {
    val h = (Help.empty /: s.definedCommands)(
      (a, b) =>
        a ++ (try b.help(s)
        catch { case NonFatal(_) => Help.empty }))
    val helpCommands = h.detail.keySet
    val spacedArg = singleArgument(helpCommands).?
    applyEffect(spacedArg)(runHelp(s, h))
  }

  def runHelp(s: State, h: Help)(arg: Option[String]): State = {
    val message = try Help.message(h, arg)
    catch { case NonFatal(ex) => ex.toString }
    System.out.println(message)
    s
  }

  def completionsCommand: Command =
    Command(CompletionsCommand, CompletionsBrief, CompletionsDetailed)(completionsParser)(
      runCompletions(_)(_))

  def completionsParser(state: State): Parser[String] = {
    val notQuoted = (NotQuoted ~ any.*) map { case (nq, s) => nq ++ s }
    val quotedOrUnquotedSingleArgument = Space ~> (StringVerbatim | StringEscapable | notQuoted)
    token(quotedOrUnquotedSingleArgument ?? "" examples ("", " "))
  }

  def runCompletions(state: State)(input: String): State = {
    Parser.completions(state.combinedParser, input, 9).get map { c =>
      if (c.isEmpty) input else input + c.append
    } foreach { c =>
      System.out.println("[completions] " + c.replaceAll("\n", " "))
    }
    state
  }

  def multiParser(s: State): Parser[List[String]] = {
    val nonSemi = token(charClass(_ != ';').+, hide = const(true))
    val semi = token(';' ~> OptSpace)
    val part = semi flatMap (_ =>
      matched((s.combinedParser & nonSemi) | nonSemi) <~ token(OptSpace))
    (part map (_.trim)).+ map (_.toList)
  }

  def multiApplied(s: State): Parser[() => State] =
    Command.applyEffect(multiParser(s))(_ ::: s)

  def multi: Command = Command.custom(multiApplied, Help(Multi, MultiBrief, MultiDetailed))

  lazy val otherCommandParser: State => Parser[String] =
    (s: State) => token(OptSpace ~> combinedLax(s, NotSpaceClass ~ any.*))

  def combinedLax(s: State, any: Parser[_]): Parser[String] =
    matched(s.combinedParser | token(any, hide = const(true)))

  def ifLast: Command =
    Command(IfLast, Help.more(IfLast, IfLastDetailed))(otherCommandParser)((s, arg) =>
      if (s.remainingCommands.isEmpty) arg :: s else s)

  def append: Command =
    Command(AppendCommand, Help.more(AppendCommand, AppendLastDetailed))(otherCommandParser)(
      (s, arg) => s.copy(remainingCommands = s.remainingCommands :+ Exec(arg, s.source)))

  def setOnFailure: Command =
    Command(OnFailure, Help.more(OnFailure, OnFailureDetailed))(otherCommandParser)((s, arg) =>
      s.copy(onFailure = Some(Exec(arg, s.source))))

  private[sbt] def compatCommands = Seq(
    Command.command(Compat.ClearOnFailure) { s =>
      s.log.warn(Compat.ClearOnFailureDeprecated)
      s.copy(onFailure = None)
    },
    Command.arb(
      s =>
        token(Compat.OnFailure, hide = const(true))
          .flatMap(_ => otherCommandParser(s))) { (s, arg) =>
      s.log.warn(Compat.OnFailureDeprecated)
      s.copy(onFailure = Some(Exec(arg, s.source)))
    },
    Command.command(Compat.FailureWall) { s =>
      s.log.warn(Compat.FailureWallDeprecated)
      s
    }
  )

  def clearOnFailure: Command = Command.command(ClearOnFailure)(s => s.copy(onFailure = None))

  def stashOnFailure: Command =
    Command.command(StashOnFailure)(s =>
      s.copy(onFailure = None).update(OnFailureStack)(s.onFailure :: _.toList.flatten))

  def popOnFailure: Command = Command.command(PopOnFailure) { s =>
    val stack = s.get(OnFailureStack).getOrElse(Nil)
    val updated =
      if (stack.isEmpty) s.remove(OnFailureStack) else s.put(OnFailureStack, stack.tail)
    updated.copy(onFailure = stack.headOption.flatten)
  }

  def reboot: Command =
    Command(RebootCommand, Help.more(RebootCommand, RebootDetailed))(rebootOptionParser) {
      case (s, (full, currentOnly)) =>
        s.reboot(full, currentOnly)
    }

  @deprecated("Use rebootOptionParser", "1.1.0")
  def rebootParser(s: State): Parser[Boolean] =
    rebootOptionParser(s) map { case (full, currentOnly) => full }

  private[sbt] def rebootOptionParser(s: State): Parser[(Boolean, Boolean)] =
    token(
      Space ~> (("full" ^^^ ((true, false))) |
        ("dev" ^^^ ((false, true))))) ?? ((false, false))

  def call: Command =
    Command(ApplyCommand, Help.more(ApplyCommand, ApplyDetailed))(_ => callParser) {
      case (state, (cp, args)) =>
        val parentLoader = getClass.getClassLoader
        def argsStr = args mkString ", "
        def cpStr = cp mkString File.pathSeparator
        def fromCpStr = if (cp.isEmpty) "" else s" from $cpStr"
        state.log info s"Applying State transformations $argsStr$fromCpStr"
        val loader =
          if (cp.isEmpty) parentLoader else toLoader(cp.map(f => new File(f)), parentLoader)
        val loaded =
          args.map(arg => ModuleUtilities.getObject(arg, loader).asInstanceOf[State => State])
        (state /: loaded)((s, obj) => obj(s))
    }

  def callParser: Parser[(Seq[String], Seq[String])] =
    token(Space) ~> ((classpathOptionParser ?? Nil) ~ rep1sep(className, token(Space)))

  private[this] def className: Parser[String] = {
    val base = StringBasic & not('-' ~> any.*, "Class name cannot start with '-'.")
    def single(s: String) = Completions.single(Completion.displayOnly(s))
    val compl = TokenCompletions.fixed((seen, _) =>
      if (seen.startsWith("-")) Completions.nil else single("<class name>"))
    token(base, compl)
  }

  private[this] def classpathOptionParser: Parser[Seq[String]] =
    token(("-cp" | "-classpath") ~> Space) ~> classpathStrings <~ token(Space)

  private[this] def classpathStrings: Parser[Seq[String]] =
    token(StringBasic.map(s => IO.pathSplit(s).toSeq), "<classpath>")

  def exit: Command = Command.command(TerminateAction, exitBrief, exitBrief)(_ exit true)

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
      val logError = (msg: String) => s.log.error(msg)
      val hp = s get historyPath getOrElse None
      val lines = hp.toList.flatMap(p => IO.readLines(p)).toIndexedSeq
      histFun(CHistory(lines, hp, logError)) match {
        case Some(commands) =>
          commands foreach println //printing is more appropriate than logging
          (commands ::: s).continue
        case None => s.fail
      }
    }

  def oldshell: Command = Command.command(OldShell, Help.more(Shell, OldShellDetailed)) { s =>
    val history = (s get historyPath) getOrElse Some(new File(s.baseDir, ".history"))
    val prompt = (s get shellPrompt) match { case Some(pf) => pf(s); case None => "> " }
    val reader = new FullReader(history, s.combinedParser)
    val line = reader.readLine(prompt)
    line match {
      case Some(line) =>
        val newState = s
          .copy(
            onFailure = Some(Exec(Shell, None)),
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
    (token(Space) ~> repsep(StringBasic, token(Space))) | (token(EOF) map (_ => Nil))

  def runClient(s0: State, inputArg: Seq[String]): State = {
    val arguments = inputArg.toList ++
      (s0.remainingCommands match {
        case e :: Nil if e.commandLine == "shell" => Nil
        case xs                                   => xs map (_.commandLine)
      })
    NetworkClient.run(arguments)
    "exit" :: s0.copy(remainingCommands = Nil)
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
    val partiallyApplied =
      Parser(Command.combine(aliasRemoved.definedCommands)(aliasRemoved))(value)
    val arg = matched(partiallyApplied & (success(()) | (SpaceClass ~ any.*)))
    // by scheduling the expanded alias instead of directly executing,
    // we get errors on the expanded string (#598)
    arg.map(str => () => (value + str) :: state)
  }

  def delegateToAlias(name: String, orElse: Parser[() => State])(
      state: State): Parser[() => State] =
    aliases(state, (nme, _) => nme == name).headOption match {
      case None         => orElse
      case Some((n, v)) => aliasBody(n, v)(state)
    }

  val CommandAliasKey: AttributeKey[(String, String)] =
    AttributeKey[(String, String)](
      "is-command-alias",
      "Internal: marker for Commands created as aliases for another command."
    )
}
