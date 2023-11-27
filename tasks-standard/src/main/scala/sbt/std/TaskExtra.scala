/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package std

import scala.sys.process.{ BasicIO, ProcessIO, ProcessBuilder }

import sbt.internal.util.{ AList, AttributeMap }
import sbt.internal.util.Types._
import java.io.{ BufferedInputStream, BufferedReader, File, InputStream }
import sbt.io.IO
import sbt.internal.Action

sealed trait BinaryPipe {
  def binary[T](f: BufferedInputStream => T): Task[T]
  def binary[T](sid: String)(f: BufferedInputStream => T): Task[T]
  def #>(f: File): Task[Unit]
  def #>(sid: String, f: File): Task[Unit]
}
sealed trait TextPipe {
  def text[T](f: BufferedReader => T): Task[T]
  def text[T](sid: String)(f: BufferedReader => T): Task[T]
}
sealed trait TaskLines {
  def lines: Task[List[String]]
  def lines(sid: String): Task[List[String]]
}
sealed trait ProcessPipe {
  def #|(p: ProcessBuilder): Task[Int]
  def pipe(sid: String)(p: ProcessBuilder): Task[Int]
}

trait TaskExtra extends TaskExtra0 {
  final implicit def pipeToProcess[Key](
      t: Task[_]
  )(implicit streams: Task[TaskStreams[Key]], key: Task[_] => Key): ProcessPipe =
    new ProcessPipe {
      def #|(p: ProcessBuilder): Task[Int] = pipe0(None, p)
      def pipe(sid: String)(p: ProcessBuilder): Task[Int] = pipe0(Some(sid), p)
      private def pipe0(sid: Option[String], p: ProcessBuilder): Task[Int] =
        streams.mapN { s =>
          val in = s.readBinary(key(t), sid)
          val pio = TaskExtra
            .processIO(s)
            .withInput(out => { BasicIO.transferFully(in, out); out.close() })
          (p run pio).exitValue()
        }
    }

  final implicit def binaryPipeTask[Key](
      in: Task[_]
  )(implicit streams: Task[TaskStreams[Key]], key: Task[_] => Key): BinaryPipe =
    new BinaryPipe {
      def binary[T](f: BufferedInputStream => T): Task[T] = pipe0(None, f)
      def binary[T](sid: String)(f: BufferedInputStream => T): Task[T] = pipe0(Some(sid), f)

      def #>(f: File): Task[Unit] = pipe0(None, toFile(f))
      def #>(sid: String, f: File): Task[Unit] = pipe0(Some(sid), toFile(f))

      private def pipe0[T](sid: Option[String], f: BufferedInputStream => T): Task[T] =
        streams.mapN { s =>
          f(s.readBinary(key(in), sid))
        }

      private def toFile(f: File) = (in: InputStream) => IO.transfer(in, f)
    }
  final implicit def textPipeTask[Key](
      in: Task[_]
  )(implicit streams: Task[TaskStreams[Key]], key: Task[_] => Key): TextPipe = new TextPipe {
    def text[T](f: BufferedReader => T): Task[T] = pipe0(None, f)
    def text[T](sid: String)(f: BufferedReader => T): Task[T] = pipe0(Some(sid), f)

    private def pipe0[T](sid: Option[String], f: BufferedReader => T): Task[T] =
      streams.mapN { s =>
        f(s.readText(key(in), sid))
      }
  }
  final implicit def linesTask[Key](
      in: Task[_]
  )(implicit streams: Task[TaskStreams[Key]], key: Task[_] => Key): TaskLines = new TaskLines {
    def lines: Task[List[String]] = lines0(None)
    def lines(sid: String): Task[List[String]] = lines0(Some(sid))

    private def lines0[T](sid: Option[String]): Task[List[String]] =
      streams map { s =>
        IO.readLines(s.readText(key(in), sid))
      }
  }
  implicit def processToTask(p: ProcessBuilder)(implicit streams: Task[TaskStreams[_]]): Task[Int] =
    streams map { s =>
      val pio = TaskExtra.processIO(s)
      (p run pio).exitValue()
    }
}

object TaskExtra extends TaskExtra {
  def processIO(s: TaskStreams[_]): ProcessIO = {
    def transfer(id: String) = (in: InputStream) => BasicIO.transferFully(in, s.binary(id))
    new ProcessIO(_.close(), transfer(s.outID), transfer(s.errorID))
  }
}
