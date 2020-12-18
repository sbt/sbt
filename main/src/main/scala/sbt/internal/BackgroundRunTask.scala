/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import BasicCommandStrings.CompleteExec
import sbt.internal.util.{ Prompt, Terminal, Util }
import sbt.util.Logger
import scala.util.Try

private[sbt] object BackgroundRunTask {
  def apply(term: Terminal, name: String, logger: Logger, state: State, scope: Def.ScopedKey[_])(
      f: () => Try[Unit]
  ): Unit = {
    val result = new java.util.concurrent.LinkedBlockingQueue[Boolean]
    val blocked = new Prompt.Blocked(() => result.take)
    val execMap =
      state.get(BasicCommands.execMap).getOrElse(Map.empty).map { case (k, v) => v -> k }
    state.currentCommand.foreach { exec =>
      exec.execId.foreach(id => blocked.setExecID(execMap.getOrElse(id, id)))
    }
    term.setPrompt(blocked)

    val runnable: Runnable = () =>
      try {
        blocked.setMainThread(Thread.currentThread)
        val tryRes = f()
        val res = tryRes.isSuccess
        tryRes.failed.foreach { t =>
          logger.err(t.toString)
          val highlight =
            state.get(Keys.taskTerminal).getOrElse(Terminal.get).isColorEnabled
          val contextDisplay =
            Project.showContextKey(state, if (highlight) Some(scala.Console.RED) else None)
          logger.error(s"(${contextDisplay.show(scope)}) $t")
        }
        val execs = blocked.getExecs.toList.map {
          case (cl, id) => Exec(cl, id, Some(CommandSource(term.name)))
        }
        val filtered =
          if (res) execs
          else execs.dropWhile(!_.commandLine.startsWith(CompleteExec)).tail
        StandardMain.exchange.addBulk(filtered)
      } catch { case t: Exception => Util.ignoreResult(result.put(false)) } finally {
        term.setPrompt(Prompt.Pending)
        val exchange = StandardMain.exchange
        exchange
          .channelForName(term.name)
          .foreach(c => exchange.withState(s => c.prompt(ConsolePromptEvent(s))))
      }
    val thread = new Thread(runnable, s"${term.name}-$name")
    thread.setDaemon(true)
    thread.start()
  }
}
