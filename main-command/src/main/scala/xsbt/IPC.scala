/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package xsbt

import java.io.{ BufferedReader, BufferedWriter, InputStreamReader, OutputStreamWriter }
import java.net.{ InetAddress, ServerSocket, Socket }

import scala.util.control.NonFatal

object IPC {
  private val portMin = 1025
  private val portMax = 65536
  private val loopback = InetAddress.getByName(null) // loopback

  def client[T](port: Int)(f: IPC => T): T =
    ipc(new Socket(loopback, port))(f)

  def pullServer[T](f: Server => T): T = {
    val server = makeServer
    try { f(new Server(server)) } finally { server.close() }
  }
  def unmanagedServer: Server = new Server(makeServer)
  def makeServer: ServerSocket = {
    val random = new java.util.Random
    def nextPort = random.nextInt(portMax - portMin + 1) + portMin
    def createServer(attempts: Int): ServerSocket =
      if (attempts > 0)
        try { new ServerSocket(nextPort, 1, loopback) } catch {
          case NonFatal(_) => createServer(attempts - 1)
        } else
        sys.error("Could not connect to socket: maximum attempts exceeded")
    createServer(10)
  }
  def server[T](f: IPC => Option[T]): T = serverImpl(makeServer, f)
  def server[T](port: Int)(f: IPC => Option[T]): T =
    serverImpl(new ServerSocket(port, 1, loopback), f)
  private def serverImpl[T](server: ServerSocket, f: IPC => Option[T]): T = {
    def listen(): T = {
      ipc(server.accept())(f) match {
        case Some(done) => done
        case None       => listen()
      }
    }

    try { listen() } finally { server.close() }
  }
  private def ipc[T](s: Socket)(f: IPC => T): T =
    try { f(new IPC(s)) } finally { s.close() }

  final class Server private[IPC] (s: ServerSocket) {
    def port = s.getLocalPort
    def close() = s.close()
    def isClosed: Boolean = s.isClosed
    def connection[T](f: IPC => T): T = IPC.ipc(s.accept())(f)
  }
}
final class IPC private (s: Socket) {
  def port = s.getLocalPort
  private val in = new BufferedReader(new InputStreamReader(s.getInputStream))
  private val out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream))

  def send(s: String) = { out.write(s); out.newLine(); out.flush() }
  def receive: String = in.readLine()
}
