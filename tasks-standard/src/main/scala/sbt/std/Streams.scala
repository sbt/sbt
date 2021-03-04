/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package std

import java.io.{ File => _, _ }
import java.util.concurrent.ConcurrentHashMap

import sbt.internal.io.DeferredWriter
import sbt.internal.util.ManagedLogger
import sbt.internal.util.Util.nil
import sbt.io.IO
import sbt.io.syntax._
import sbt.util._

// no longer specific to Tasks, so 'TaskStreams' should be renamed
/**
 * Represents a set of streams associated with a context.
 * In sbt, this is a named set of streams for a particular scoped key.
 * For example, logging for test:compile is by default sent to the "out" stream in the test:compile context.
 */
sealed trait TaskStreams[Key] {

  /** The default stream ID, used when an ID is not provided. */
  def default = outID

  def outID = "out"
  def errorID = "err"

  def getInput(key: Key, sid: String = default): Input
  def getOutput(sid: String = default): Output

  /**
   * Provides a reader to read text from the stream `sid` for `key`.
   * It is the caller's responsibility to coordinate writing to the stream.
   * That is, no synchronization or ordering is provided and so this method should only be called when writing is complete.
   */
  def readText(key: Key, sid: String = default): BufferedReader

  /**
   * Provides an output stream to read from the stream `sid` for `key`.
   * It is the caller's responsibility to coordinate writing to the stream.
   * That is, no synchronization or ordering is provided and so this method should only be called when writing is complete.
   */
  def readBinary(a: Key, sid: String = default): BufferedInputStream

  final def readText(a: Key, sid: Option[String]): BufferedReader = readText(a, getID(sid))
  final def readBinary(a: Key, sid: Option[String]): BufferedInputStream =
    readBinary(a, getID(sid))

  def key: Key

  /** Provides a writer for writing text to the stream with the given ID. */
  def text(sid: String = default): PrintWriter

  /** Provides an output stream for writing to the stream with the given ID. */
  def binary(sid: String = default): BufferedOutputStream

  /** A cache directory that is unique to the context of this streams instance.*/
  def cacheDirectory: File

  def cacheStoreFactory: CacheStoreFactory

  // default logger
  /** Obtains the default logger. */
  final lazy val log: ManagedLogger = log(default)

  /** Creates a Logger that logs to stream with ID `sid`.*/
  def log(sid: String): ManagedLogger

  private[this] def getID(s: Option[String]) = s getOrElse default
}
sealed trait ManagedStreams[Key] extends TaskStreams[Key] {
  def open(): Unit
  def close(): Unit
  def isClosed: Boolean
}

trait Streams[Key] {
  def apply(a: Key): ManagedStreams[Key]
  def use[T](key: Key)(f: TaskStreams[Key] => T): T = {
    val s = apply(key)
    s.open()
    try {
      f(s)
    } finally {
      s.close()
    }
  }
}
trait CloseableStreams[Key] extends Streams[Key] with java.io.Closeable
object Streams {
  private[this] val closeQuietly = (c: Closeable) =>
    try {
      c.close()
    } catch { case _: IOException => () }
  private[this] val streamLocks = new ConcurrentHashMap[File, AnyRef]()

  def closeable[Key](delegate: Streams[Key]): CloseableStreams[Key] = new CloseableStreams[Key] {
    private[this] val streams = new collection.mutable.HashMap[Key, ManagedStreams[Key]]

    def apply(key: Key): ManagedStreams[Key] =
      synchronized {
        streams.get(key) match {
          case Some(s) if !s.isClosed => s
          case _ =>
            val newS = delegate(key)
            streams.put(key, newS)
            newS
        }
      }

    def close(): Unit =
      synchronized { streams.values.foreach(_.close()); streams.clear() }
  }

  @deprecated("Use constructor without converter", "1.4")
  def apply[Key, J: sjsonnew.IsoString](
      taskDirectory: Key => File,
      name: Key => String,
      mkLogger: (Key, PrintWriter) => ManagedLogger,
      converter: sjsonnew.SupportConverter[J],
  ): Streams[Key] = apply[Key](taskDirectory, name, mkLogger)

  @deprecated("Use constructor without converter", "1.4")
  private[sbt] def apply[Key, J: sjsonnew.IsoString](
      taskDirectory: Key => File,
      name: Key => String,
      mkLogger: (Key, PrintWriter) => ManagedLogger,
      converter: sjsonnew.SupportConverter[J],
      mkFactory: (File, sjsonnew.SupportConverter[J]) => CacheStoreFactory
  ): Streams[Key] = apply[Key](taskDirectory, name, mkLogger, mkFactory(_, converter))

  def apply[Key](
      taskDirectory: Key => File,
      name: Key => String,
      mkLogger: (Key, PrintWriter) => ManagedLogger,
  ): Streams[Key] =
    apply(
      taskDirectory,
      name,
      mkLogger,
      file => new DirectoryStoreFactory(file)
    )
  private[sbt] def apply[Key](
      taskDirectory: Key => File,
      name: Key => String,
      mkLogger: (Key, PrintWriter) => ManagedLogger,
      mkFactory: File => CacheStoreFactory
  ): Streams[Key] = new Streams[Key] {

    def apply(a: Key): ManagedStreams[Key] = new ManagedStreams[Key] {
      private[this] var opened: List[Closeable] = nil
      private[this] var closed = false

      def getInput(a: Key, sid: String = default): Input =
        make(a, sid)(f => new FileInput(f))

      def getOutput(sid: String = default): Output =
        make(a, sid)(f => new FileOutput(f))

      def readText(a: Key, sid: String = default): BufferedReader =
        make(a, sid)(
          f => new BufferedReader(new InputStreamReader(new FileInputStream(f), IO.defaultCharset))
        )

      def readBinary(a: Key, sid: String = default): BufferedInputStream =
        make(a, sid)(f => new BufferedInputStream(new FileInputStream(f)))

      def text(sid: String = default): PrintWriter =
        make(a, sid)(
          f =>
            new PrintWriter(
              new DeferredWriter(
                new BufferedWriter(
                  new OutputStreamWriter(new FileOutputStream(f), IO.defaultCharset)
                )
              )
            )
        )

      def binary(sid: String = default): BufferedOutputStream =
        make(a, sid)(f => new BufferedOutputStream(new FileOutputStream(f)))

      lazy val cacheDirectory: File = {
        val dir = taskDirectory(a)
        IO.createDirectory(dir)
        dir
      }

      lazy val cacheStoreFactory: CacheStoreFactory = mkFactory(cacheDirectory)

      def log(sid: String): ManagedLogger = mkLogger(a, text(sid))

      def make[T <: Closeable](a: Key, sid: String)(f: File => T): T = synchronized {
        checkOpen()
        val file = taskDirectory(a) / sid
        val parent = file.getParentFile
        val newLock = new AnyRef
        val lock = streamLocks.putIfAbsent(parent, newLock) match {
          case null => newLock
          case l    => l
        }
        try lock.synchronized {
          if (!file.exists) IO.touch(file, setModified = false)
        } finally {
          streamLocks.remove(parent)
          ()
        }
        val t = f(file)
        opened ::= (t: Closeable)
        t
      }

      def key: Key = a
      def open(): Unit = ()
      def isClosed: Boolean = synchronized { closed }

      def close(): Unit = synchronized {
        if (!closed) {
          closed = true
          opened foreach closeQuietly
        }
      }
      def checkOpen(): Unit = synchronized {
        if (closed) sys.error("Streams for '" + name(a) + "' have been closed.")
      }
    }
  }
}
