package sbt.internal

import org.scalatest.freespec.AnyFreeSpec

import java.net.ProtocolFamily
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Paths
import scala.util.Random

class BootServerSocketTest extends AnyFreeSpec {
  private def withServerAndClient[A](action: (SocketWrapper, SocketChannel) => A): A = {
    val tmp = Files.createTempDirectory(this.getClass.getSimpleName)
    val path = BootServerSocket.socketLocation(tmp)
    val dir = Paths.get(path).getParent
    try {
      if (!Files.isDirectory(dir)) {
        Files.createDirectories(dir)
      }
      val serverSocket = BootServerSocket.newJdkUnixDomainSocket(path)
      try {
        val openMethod = classOf[SocketChannel].getMethod("open", classOf[ProtocolFamily])
        val client =
          openMethod.invoke(null, BootServerSocket.unixProtocolFamily()).asInstanceOf[SocketChannel]
        assert(client.connect(BootServerSocket.unixDomainSocketAddress(path)))
        val server = serverSocket.accept()
        action(server, client)
      } finally {
        serverSocket.close()
      }
    } finally {
      Files.deleteIfExists(Paths.get(path))
      Files.deleteIfExists(dir)
    }
  }

  "BootServerSocket" - {
    "newJdkUnixDomainSocket" - {
      if (!scala.util.Properties.isWin && scala.util.Properties.isJavaAtLeast(17)) {
        val values: List[Byte] =
          Random.shuffle((Byte.MinValue to Byte.MaxValue).toList.map(_.toByte))

        def readAll(client: SocketChannel): List[Byte] = {
          Iterator
            .continually {
              val buf = ByteBuffer.allocate(1)
              val x = client.read(buf)
              x -> buf.get(0)
            }
            .takeWhile(_._1 != -1)
            .map(_._2)
            .toList
        }

        "write(int)" in withServerAndClient { (server, client) =>
          try {
            values.foreach(x => server.write(x))
          } finally {
            server.close()
          }
          val res = readAll(client)
          assert(res == values)
        }

        "write(byte[])" in withServerAndClient { (server, client) =>
          try {
            server.write(values.toArray)
          } finally {
            server.close()
          }
          val res = readAll(client)
          assert(res == values)
        }

        "write(byte[], int, int)" in withServerAndClient { (server, client) =>
          val offset = 10
          val length = 20
          try {
            server.write(values.toArray, offset, length)
          } finally {
            server.close()
          }
          val res = readAll(client)
          assert(res == values.slice(offset, offset + length))
        }

        "read" in withServerAndClient { (server, client) =>
          try {
            client.write(ByteBuffer.wrap(values.toArray))
          } finally {
            client.close()
          }

          val result = Iterator
            .continually(server.read())
            .takeWhile(_ != -1)
            .toList

          assert(result == values.map(_ & 0xff))
        }
      }
    }
  }
}
