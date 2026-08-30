/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.protocol

import hedgehog.{ Gen, Property, Result }
import hedgehog.core.{ ShrinkLimit, SuccessCount }
import hedgehog.runner.*
import java.io.{ EOFException, InputStream }
import java.net.{ StandardProtocolFamily, UnixDomainSocketAddress }
import java.nio.ByteBuffer
import java.nio.channels.{ ServerSocketChannel, SocketChannel }
import java.util.concurrent.{ CountDownLatch, LinkedBlockingQueue, TimeUnit }
import scala.util.Using
import scala.util.control.NonFatal
import sbt.io.IO

/**
 * Regression test: bootSocket used to wrap its channel with
 * Channels.newInputStream/newOutputStream, which share the channel's blockingLock() and deadlock
 * a blocking read against a concurrent write on the same channel. It now uses DuplexChannels,
 * which talks to the channel directly and has no such shared lock. What matters for reproducing
 * the deadlock is timing, not payload content, so the reader and writer threads are each given an
 * independent startup delay drawn from {0, 100, 300}ms to exercise the read starting well before,
 * around the same time as, and well after the write.
 */
object ClientSocketDuplexTest extends Properties:
  override def tests: List[Test] = List(
    propertyN(
      "bootSocket: a concurrent read and write on the same channel do not deadlock",
      propDuplex,
      20,
    ),
  )

  def propertyN(name: String, result: => Property, n: Int): Test =
    Test(name, result)
      .config(_.copy(testLimit = SuccessCount(n), shrinkLimit = ShrinkLimit(n * 10)))

  private val toServer: Array[Byte] = Array[Byte](1)
  private val toClient: Array[Byte] = Array[Byte](2)

  val sleepMsGen: Gen[Int] = Gen.element1(0, 100, 300)

  def propDuplex: Property =
    for
      readerSleepMs <- sleepMsGen.log("reader startup delay (ms)")
      writerSleepMs <- sleepMsGen.log("writer startup delay (ms)")
    yield runDuplexRound(readerSleepMs, writerSleepMs)

  private def runDuplexRound(readerSleepMs: Int, writerSleepMs: Int): Result =
    IO.withTemporaryDirectory: dir =>
      val path = dir.toPath.resolve("boot.sock")
      Using.resource(ServerSocketChannel.open(StandardProtocolFamily.UNIX)): serverChannel =>
        serverChannel.bind(UnixDomainSocketAddress.of(path))
        Using.resource(ClientSocket.bootSocket(path.toString)): client =>
          Using.resource(serverChannel.accept()): serverSide =>
            // The client's reader is parked waiting for toClient before the server has sent
            // anything, so it's mid-read (and would be holding blockingLock() under the old
            // Channels-based implementation) while we race the write below against it.
            val readOutcome = new LinkedBlockingQueue[Either[Throwable, Array[Byte]]]()
            val reader = new Thread(() =>
              readOutcome.put(
                try
                  Thread.sleep(readerSleepMs.toLong)
                  Right(readNBytes(client.getInputStream(), toClient.length))
                catch case NonFatal(e) => Left(e)
              )
            )
            reader.setDaemon(true)
            reader.start()

            val writeDone = new CountDownLatch(1)
            val writer = new Thread(() =>
              try
                Thread.sleep(writerSleepMs.toLong)
                client.getOutputStream().write(toServer)
              catch case NonFatal(_) => ()
              finally writeDone.countDown()
            )
            writer.setDaemon(true)
            writer.start()

            if !writeDone.await(3, TimeUnit.SECONDS) then
              Result.failure.log(
                "write blocked behind the concurrent read: possible regression of the " +
                  "Channels.newInputStream/newOutputStream blockingLock() deadlock"
              )
            else
              val fromClient = readNBytes(serverSide, toServer.length)
              serverSide.write(ByteBuffer.wrap(toClient))
              readOutcome.poll(3, TimeUnit.SECONDS) match
                case null =>
                  Result.failure.log(
                    "client's blocked read never completed after the server replied"
                  )
                case Left(e)           => Result.failure.log(s"client read failed: $e")
                case Right(fromServer) =>
                  Result.all(
                    List(
                      Result
                        .assert(fromClient.sameElements(toServer))
                        .log("server received a different payload than the client sent"),
                      Result
                        .assert(fromServer.sameElements(toClient))
                        .log("client received a different payload than the server sent"),
                    )
                  )

  private def readNBytes(in: InputStream, n: Int): Array[Byte] =
    val buf = new Array[Byte](n)
    var total = 0
    while total < n do
      val r = in.read(buf, total, n - total)
      if r < 0 then throw new EOFException(s"expected $n bytes, got $total")
      total += r
    buf

  private def readNBytes(ch: SocketChannel, n: Int): Array[Byte] =
    val bb = ByteBuffer.allocate(n)
    while bb.hasRemaining() do
      val r = ch.read(bb)
      if r < 0 then throw new EOFException(s"expected $n bytes, got ${bb.position()}")
    bb.array()
end ClientSocketDuplexTest
