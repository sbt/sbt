/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.protocol

import hedgehog.{ Gen, Property, Range, Result }
import hedgehog.core.{ ShrinkLimit, SuccessCount }
import hedgehog.runner.*
import java.io.{ EOFException, InputStream, InterruptedIOException }
import java.net.{
  ServerSocket,
  SocketException,
  SocketTimeoutException,
  StandardProtocolFamily,
  UnixDomainSocketAddress
}
import java.nio.file.{ Files, Path, Paths }
import java.nio.channels.ServerSocketChannel
import java.util.concurrent.{ LinkedBlockingQueue, TimeUnit }
import scala.util.Using
import scala.util.control.NonFatal
import sbt.io.IO

object DuplexServerSocketTest extends Properties:
  override def tests: List[Test] =
    if sbt.internal.util.Util.isWindows then Nil
    else
      List(
        propertyN("newServerSocket: round trips bytes with a client", propRoundTrip, 10),
        propertyN("newServerSocket: accept times out without a client", propTimeout, 5),
        example("newServerSocket: close ends a blocked accept", closeEndsAccept),
        propertyN("newSocket: a read times out after SO_TIMEOUT", propReadTimeout, 5),
        example("newSocket: an interrupted read leaves the socket open", interruptKeepsSocket),
      )

  def propertyN(name: String, result: => Property, n: Int): Test =
    Test(name, result)
      .config(_.copy(testLimit = SuccessCount(n), shrinkLimit = ShrinkLimit(n * 10)))

  private val payloadGen: Gen[List[Byte]] =
    Gen.list(Gen.byte(Range.constantFrom(0, Byte.MinValue, Byte.MaxValue)), Range.linear(1, 4096))

  def propRoundTrip: Property =
    for
      toServer <- payloadGen.log("to server")
      toClient <- payloadGen.log("to client")
    yield withServerSocket: (path, server) =>
      Using.resource(ClientSocket.unixSocket(path)): client =>
        Using.resource(server.accept()): serverSide =>
          serverSide.setSoTimeout(5000)
          client.getOutputStream.write(toServer.toArray)
          val received = readNBytes(serverSide.getInputStream, toServer.length)
          serverSide.getOutputStream.write(toClient.toArray)
          val replied = readNBytes(client.getInputStream, toClient.length)
          Result.all(
            List(
              Result.assert(received.sameElements(toServer)).log("server got a different payload"),
              Result.assert(replied.sameElements(toClient)).log("client got a different payload"),
              Result.assert(serverSide.getSoTimeout == 5000),
            )
          )

  def propTimeout: Property =
    for timeout <- Gen.int(Range.linear(50, 200)).log("timeout (ms)")
    yield withServerSocket: (_, server) =>
      server.setSoTimeout(timeout)
      val start = System.nanoTime
      try
        server.accept().close()
        Result.failure.log("accept returned without a client")
      catch
        case _: SocketTimeoutException =>
          val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime - start)
          Result.assert(elapsed >= timeout - 10).log(s"timed out after $elapsed ms")

  def closeEndsAccept: Result =
    withServerSocket: (_, server) =>
      val outcome = new LinkedBlockingQueue[Throwable]()
      val acceptor = new Thread(() =>
        try server.accept().close()
        catch case NonFatal(e) => outcome.put(e)
      )
      acceptor.setDaemon(true)
      acceptor.start()
      Thread.sleep(100)
      server.close()
      outcome.poll(3, TimeUnit.SECONDS) match
        case null               => Result.failure.log("accept kept blocking after close")
        case _: SocketException => Result.assert(server.isClosed)
        case e                  => Result.failure.log(s"unexpected $e")

  def propReadTimeout: Property =
    for timeout <- Gen.int(Range.linear(50, 200)).log("timeout (ms)")
    yield withServerSocket: (path, server) =>
      Using.resource(ClientSocket.unixSocket(path)): client =>
        Using.resource(server.accept()): _ =>
          client.setSoTimeout(timeout)
          val start = System.nanoTime
          try
            client.getInputStream.read()
            Result.failure.log("read returned without data")
          catch
            case _: SocketTimeoutException =>
              val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime - start)
              Result.all(
                List(
                  Result.assert(elapsed >= timeout - 10).log(s"timed out after $elapsed ms"),
                  Result.assert(!client.isClosed).log("the timeout closed the socket"),
                )
              )

  def interruptKeepsSocket: Result =
    withServerSocket: (path, server) =>
      Using.resource(ClientSocket.unixSocket(path)): client =>
        Using.resource(server.accept()): serverSide =>
          val outcome = new LinkedBlockingQueue[Throwable]()
          val reader = new Thread(() =>
            try
              client.getInputStream.read()
              ()
            catch case NonFatal(e) => outcome.offer(e): Unit
          )
          reader.setDaemon(true)
          reader.start()
          Thread.sleep(100)
          reader.interrupt()
          val thrown = outcome.poll(3, TimeUnit.SECONDS)
          serverSide.getOutputStream.write(Array[Byte](7))
          val after = client.getInputStream.read()
          Result.all(
            List(
              Result.assert(thrown.isInstanceOf[InterruptedIOException]).log(s"read threw $thrown"),
              Result.assert(!client.isClosed).log("the interrupt closed the socket"),
              Result.assert(after == 7).log(s"read $after after the interrupt"),
            )
          )

  private def withServerSocket(f: (Path, ServerSocket) => Result): Result =
    val dir = Files.createTempDirectory(Paths.get("/tmp"), "sbtdss")
    try
      val path = dir.resolve("sock")
      val ch = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
      ch.bind(UnixDomainSocketAddress.of(path))
      Using.resource(DuplexChannels.newServerSocket(ch))(server => f(path, server))
    finally IO.delete(dir.toFile)

  private def readNBytes(in: InputStream, n: Int): Array[Byte] =
    val buf = new Array[Byte](n)
    var total = 0
    while total < n do
      val r = in.read(buf, total, n - total)
      if r < 0 then throw new EOFException(s"expected $n bytes, got $total")
      total += r
    buf
end DuplexServerSocketTest
