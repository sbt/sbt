/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import java.io.PrintStream
import java.util.concurrent.atomic.{ AtomicInteger, AtomicReference }

import sbt.internal.util.ConsoleAppender.{
  ClearScreenAfterCursor,
  CursorLeft1000,
  DeleteLine,
  cursorLeft,
  cursorUp,
}

import scala.collection.mutable.ArrayBuffer

private[sbt] final class ProgressState(
    val progressLines: AtomicReference[Seq[String]],
    val padding: AtomicInteger,
    val blankZone: Int,
    val currentLineBytes: AtomicReference[ArrayBuffer[Byte]],
) {
  def this(blankZone: Int) =
    this(
      new AtomicReference(Nil),
      new AtomicInteger(0),
      blankZone,
      new AtomicReference(new ArrayBuffer[Byte]),
    )
  def reset(): Unit = {
    progressLines.set(Nil)
    padding.set(0)
    currentLineBytes.set(new ArrayBuffer[Byte])
  }
  private[util] def clearBytes(): Unit = {
    val pad = padding.get
    if (currentLineBytes.get.isEmpty && pad > 0) padding.decrementAndGet()
    currentLineBytes.set(new ArrayBuffer[Byte])
  }

  private[util] def addBytes(terminal: Terminal, bytes: ArrayBuffer[Byte]): Unit = {
    val previous = currentLineBytes.get
    val padding = this.padding.get
    val prevLineCount = if (padding > 0) terminal.lineCount(new String(previous.toArray)) else 0
    previous ++= bytes
    if (padding > 0) {
      val newLineCount = terminal.lineCount(new String(previous.toArray))
      val diff = newLineCount - prevLineCount
      this.padding.set(math.max(padding - diff, 0))
    }
  }

  private[util] def printPrompt(terminal: Terminal, printStream: PrintStream): Unit =
    if (terminal.prompt != Prompt.Running && terminal.prompt != Prompt.Batch) {
      val prefix = if (terminal.isAnsiSupported) s"$DeleteLine$CursorLeft1000" else ""
      val pmpt = prefix.getBytes ++ terminal.prompt.render().getBytes
      pmpt.foreach(b => printStream.write(b & 0xFF))
    }
  private[util] def reprint(terminal: Terminal, printStream: PrintStream): Unit = {
    printPrompt(terminal, printStream)
    if (progressLines.get.nonEmpty) {
      val lines = printProgress(terminal, terminal.getLastLine.getOrElse(""))
      printStream.print(ClearScreenAfterCursor + lines)
    }
  }

  private[util] def printProgress(
      terminal: Terminal,
      lastLine: String
  ): String = {
    val previousLines = progressLines.get
    if (previousLines.nonEmpty) {
      val currentLength = previousLines.foldLeft(0)(_ + terminal.lineCount(_))
      val (height, width) = terminal.getLineHeightAndWidth(lastLine)
      val left = cursorLeft(1000) // resets the position to the left
      val offset = width > 0
      val pad = math.max(padding.get - height, 0)
      val start = (if (offset) "\n" else "")
      val totalSize = currentLength + blankZone + pad
      val blank = left + s"\n$DeleteLine" * (totalSize - currentLength)
      val lines = previousLines.mkString(DeleteLine, s"\n$DeleteLine", s"\n$DeleteLine")
      val resetCursorUp = cursorUp(totalSize + (if (offset) 1 else 0))
      val resetCursor = resetCursorUp + left + lastLine
      start + blank + lines + resetCursor
    } else {
      ClearScreenAfterCursor
    }
  }
}

private[sbt] object ProgressState {

  /**
   * Receives a new task report and replaces the old one. In the event that the new
   * report has fewer lines than the previous report, padding lines are added on top
   * so that the console log lines remain contiguous. When a console line is printed
   * at the info or greater level, we can decrement the padding because the console
   * line will have filled in the blank line.
   */
  private[sbt] def updateProgressState(
      pe: ProgressEvent,
      terminal: Terminal
  ): Unit = {
    val state = terminal.progressState
    val isRunning = terminal.prompt == Prompt.Running
    val isBatch = terminal.prompt == Prompt.Batch
    val isWatch = terminal.prompt == Prompt.Watch
    val noPrompt = terminal.prompt == Prompt.NoPrompt
    if (terminal.isSupershellEnabled) {
      if (!pe.skipIfActive.getOrElse(false) || (!isRunning && !isBatch)) {
        terminal.withPrintStream { ps =>
          val info =
            if ((isRunning || isBatch || noPrompt) && pe.channelName
                  .fold(true)(_ == terminal.name)) {
              pe.items.map { item =>
                val elapsed = item.elapsedMicros / 1000000L
                s"  | => ${item.name} ${elapsed}s"
              }
            } else {
              pe.command.toSeq.flatMap { cmd =>
                val tail = if (isWatch) Nil else "enter 'cancel' to stop evaluation" :: Nil
                s"sbt server is running '$cmd'" :: tail
              }
            }

          val currentLength = info.foldLeft(0)(_ + terminal.lineCount(_))
          val previousLines = state.progressLines.getAndSet(info)
          val prevLength = previousLines.foldLeft(0)(_ + terminal.lineCount(_))
          val lastLine = terminal.prompt match {
            case Prompt.Running | Prompt.Batch => terminal.getLastLine.getOrElse("")
            case a                             => a.render()
          }
          val prevSize = prevLength + state.padding.get

          val newPadding = math.max(0, prevSize - currentLength)
          state.padding.set(newPadding)
          state.printPrompt(terminal, ps)
          ps.print(state.printProgress(terminal, lastLine))
          ps.flush()
        }
      } else if (state.progressLines.get.nonEmpty) {
        state.progressLines.set(Nil)
        terminal.withPrintStream { ps =>
          val lastLine = terminal.getLastLine.getOrElse("")
          ps.print(lastLine + ClearScreenAfterCursor)
          ps.flush()
        }
      }
    }
  }
}
