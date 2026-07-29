/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package server

import java.util.concurrent.{ ArrayBlockingQueue, ConcurrentHashMap }
import java.util.UUID
import sbt.internal.protocol.{
  JsonRpcNotificationMessage,
  JsonRpcRequestMessage,
  JsonRpcResponseMessage
}
import sbt.protocol.Serialization.{
  attach,
  systemIn,
  getTerminalAttributes,
  setTerminalAttributes,
  terminalCapabilities,
  terminalGetSize,
  terminalPropertiesQuery,
  terminalSetEcho,
  terminalSetSize,
  terminalSetRawMode,
}
import sjsonnew.support.scalajson.unsafe.Converter
import sbt.internal.util.Util
import sbt.protocol.{
  Attach,
  TerminalAttributesQuery,
  TerminalAttributesResponse,
  TerminalCapabilitiesQuery,
  TerminalCapabilitiesResponse,
  TerminalPropertiesResponse,
  TerminalGetSizeQuery,
  TerminalGetSizeResponse,
  TerminalSetAttributesCommand,
  TerminalSetEchoCommand,
  TerminalSetSizeCommand,
  TerminalSetRawModeCommand,
}
import sbt.protocol.codec.JsonProtocol.given

object VirtualTerminal {
  private val pendingTerminalProperties =
    new ConcurrentHashMap[(String, String), ArrayBlockingQueue[TerminalPropertiesResponse]]()
  private val pendingTerminalCapabilities =
    new ConcurrentHashMap[(String, String), ArrayBlockingQueue[TerminalCapabilitiesResponse]]
  private val pendingTerminalAttributes =
    new ConcurrentHashMap[(String, String), ArrayBlockingQueue[TerminalAttributesResponse]]
  private val pendingTerminalSetAttributes =
    new ConcurrentHashMap[(String, String), ArrayBlockingQueue[Unit]]
  private val pendingTerminalSetSize =
    new ConcurrentHashMap[(String, String), ArrayBlockingQueue[Unit]]
  private val pendingTerminalGetSize =
    new ConcurrentHashMap[(String, String), ArrayBlockingQueue[TerminalGetSizeResponse]]
  private val pendingTerminalSetEcho =
    new ConcurrentHashMap[(String, String), ArrayBlockingQueue[Unit]]
  private val pendingTerminalSetRawMode =
    new ConcurrentHashMap[(String, String), ArrayBlockingQueue[Unit]]
  private[sbt] def sendTerminalPropertiesQuery(
      channelName: String,
      jsonRpcRequest: (String, String, String) => Unit
  ): ArrayBlockingQueue[TerminalPropertiesResponse] = {
    val id = UUID.randomUUID.toString
    val queue = new ArrayBlockingQueue[TerminalPropertiesResponse](1)
    pendingTerminalProperties.put((channelName, id), queue)
    jsonRpcRequest(id, terminalPropertiesQuery, "")
    queue
  }
  private[sbt] def sendTerminalCapabilitiesQuery(
      channelName: String,
      jsonRpcRequest: (String, String, TerminalCapabilitiesQuery) => Unit,
      query: TerminalCapabilitiesQuery,
  ): ArrayBlockingQueue[TerminalCapabilitiesResponse] = {
    val id = UUID.randomUUID.toString
    val queue = new ArrayBlockingQueue[TerminalCapabilitiesResponse](1)
    pendingTerminalCapabilities.put((channelName, id), queue)
    jsonRpcRequest(id, terminalCapabilities, query)
    queue
  }
  private[sbt] def expireTerminalPropertiesQuery(
      channelName: String,
      queue: ArrayBlockingQueue[TerminalPropertiesResponse],
  ): Option[TerminalPropertiesResponse] = {
    import scala.jdk.CollectionConverters.*
    pendingTerminalProperties.asScala.collectFirst {
      case (k @ (`channelName`, _), q) if q eq queue => k
    } match {
      case Some(k) if pendingTerminalProperties.remove(k) != null => Option(queue.poll())
      // The response handler won the removal: its put is imminent, wait it out briefly.
      case _ => Option(queue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS))
    }
  }
  private[sbt] def cancelRequests(name: String): Unit = {
    import scala.jdk.CollectionConverters.*
    def drain[A](
        map: ConcurrentHashMap[(String, String), ArrayBlockingQueue[A]],
        default: A
    ): Unit =
      map.asScala.foreach {
        case (k @ (`name`, _), q) =>
          map.remove(k)
          Util.ignoreResult(q.offer(default))
        case _ =>
      }
    drain(pendingTerminalCapabilities, TerminalCapabilitiesResponse(None, None, None))
    drain(pendingTerminalProperties, TerminalPropertiesResponse(0, 0, false, false, false, false))
    drain(pendingTerminalAttributes, TerminalAttributesResponse("", "", "", "", ""))
    drain(pendingTerminalSetAttributes, ())
    drain(pendingTerminalSetSize, ())
    drain(pendingTerminalGetSize, TerminalGetSizeResponse(1, 1))
    drain(pendingTerminalSetEcho, ())
    drain(pendingTerminalSetRawMode, ())
  }
  private[sbt] def sendTerminalAttributesQuery(
      channelName: String,
      jsonRpcRequest: (String, String, TerminalAttributesQuery) => Unit,
  ): ArrayBlockingQueue[TerminalAttributesResponse] = {
    val id = UUID.randomUUID.toString
    val queue = new ArrayBlockingQueue[TerminalAttributesResponse](1)
    pendingTerminalAttributes.put((channelName, id), queue)
    jsonRpcRequest(id, getTerminalAttributes, TerminalAttributesQuery())
    queue
  }
  private[sbt] def setTerminalAttributesCommand(
      channelName: String,
      jsonRpcRequest: (String, String, TerminalSetAttributesCommand) => Unit,
      query: TerminalSetAttributesCommand
  ): ArrayBlockingQueue[Unit] = {
    val id = UUID.randomUUID.toString
    val queue = new ArrayBlockingQueue[Unit](1)
    pendingTerminalSetAttributes.put((channelName, id), queue)
    jsonRpcRequest(id, setTerminalAttributes, query)
    queue
  }

  private[sbt] def setTerminalSize(
      channelName: String,
      jsonRpcRequest: (String, String, TerminalSetSizeCommand) => Unit,
      query: TerminalSetSizeCommand
  ): ArrayBlockingQueue[Unit] = {
    val id = UUID.randomUUID.toString
    val queue = new ArrayBlockingQueue[Unit](1)
    pendingTerminalSetSize.put((channelName, id), queue)
    jsonRpcRequest(id, terminalSetSize, query)
    queue
  }

  private[sbt] def getTerminalSize(
      channelName: String,
      jsonRpcRequest: (String, String, TerminalGetSizeQuery) => Unit,
  ): ArrayBlockingQueue[TerminalGetSizeResponse] = {
    val id = UUID.randomUUID.toString
    val query = TerminalGetSizeQuery()
    val queue = new ArrayBlockingQueue[TerminalGetSizeResponse](1)
    pendingTerminalGetSize.put((channelName, id), queue)
    jsonRpcRequest(id, terminalGetSize, query)
    queue
  }

  private[sbt] def setTerminalEcho(
      channelName: String,
      jsonRpcRequest: (String, String, TerminalSetEchoCommand) => Unit,
      query: TerminalSetEchoCommand
  ): ArrayBlockingQueue[Unit] = {
    val id = UUID.randomUUID.toString
    val queue = new ArrayBlockingQueue[Unit](1)
    pendingTerminalSetEcho.put((channelName, id), queue)
    jsonRpcRequest(id, terminalSetEcho, query)
    queue
  }

  private[sbt] def setTerminalRawMode(
      channelName: String,
      jsonRpcRequest: (String, String, TerminalSetRawModeCommand) => Unit,
      query: TerminalSetRawModeCommand
  ): ArrayBlockingQueue[Unit] = {
    val id = UUID.randomUUID.toString
    val queue = new ArrayBlockingQueue[Unit](1)
    pendingTerminalSetRawMode.put((channelName, id), queue)
    jsonRpcRequest(id, terminalSetRawMode, query)
    queue
  }

  val handler = ServerHandler { cb =>
    ServerIntent(requestHandler(cb), responseHandler(cb), notificationHandler(cb))
  }
  type Handler[R] = ServerCallback => PartialFunction[R, Unit]
  private val requestHandler: Handler[JsonRpcRequestMessage] =
    callback => {
      case r if r.method == attach =>
        val isInteractive = r.params
          .flatMap(Converter.fromJson[Attach](_).toOption.map(_.interactive))
          .exists(identity)
        StandardMain.exchange.channelForName(callback.name) match {
          case Some(nc: NetworkChannel) => nc.setInteractive(r.id, isInteractive)
          case _                        =>
        }
    }
  private val responseHandler: Handler[JsonRpcResponseMessage] =
    callback => {
      case r if pendingTerminalProperties.get((callback.name, r.id)) != null =>
        val response =
          r.result.flatMap(Converter.fromJson[TerminalPropertiesResponse](_).toOption)
        pendingTerminalProperties.remove((callback.name, r.id)) match {
          case null   =>
          case buffer =>
            buffer.put(
              response.getOrElse(TerminalPropertiesResponse(0, 0, false, false, false, false))
            )
        }
      case r if pendingTerminalCapabilities.get((callback.name, r.id)) != null =>
        val response =
          r.result.flatMap(
            Converter.fromJson[TerminalCapabilitiesResponse](_).toOption
          )
        pendingTerminalCapabilities.remove((callback.name, r.id)) match {
          case null   =>
          case buffer =>
            buffer.put(response.getOrElse(TerminalCapabilitiesResponse(None, None, None)))
        }
      case r if pendingTerminalAttributes.get((callback.name, r.id)) != null =>
        val response =
          r.result.flatMap(Converter.fromJson[TerminalAttributesResponse](_).toOption)
        pendingTerminalAttributes.remove((callback.name, r.id)) match {
          case null   =>
          case buffer =>
            buffer.put(response.getOrElse(TerminalAttributesResponse("", "", "", "", "")))
        }
      case r if pendingTerminalSetAttributes.get((callback.name, r.id)) != null =>
        pendingTerminalSetAttributes.remove((callback.name, r.id)) match {
          case null   =>
          case buffer => buffer.put(())
        }
      case r if pendingTerminalSetSize.get((callback.name, r.id)) != null =>
        pendingTerminalSetSize.remove((callback.name, r.id)) match {
          case null   =>
          case buffer => buffer.put(())
        }
      case r if pendingTerminalGetSize.get((callback.name, r.id)) != null =>
        val response =
          r.result.flatMap(Converter.fromJson[TerminalGetSizeResponse](_).toOption)
        pendingTerminalGetSize.remove((callback.name, r.id)) match {
          case null   =>
          case buffer => buffer.put(response.getOrElse(TerminalGetSizeResponse(1, 1)))
        }
      case r if pendingTerminalSetEcho.get((callback.name, r.id)) != null =>
        pendingTerminalSetEcho.remove((callback.name, r.id)) match {
          case null   =>
          case buffer => buffer.put(())
        }
      case r if pendingTerminalSetRawMode.get((callback.name, r.id)) != null =>
        pendingTerminalSetRawMode.remove((callback.name, r.id)) match {
          case null   =>
          case buffer => buffer.put(())
        }
    }
  private val notificationHandler: Handler[JsonRpcNotificationMessage] =
    callback => {
      case n if n.method == systemIn =>
        import sjsonnew.BasicJsonProtocol.*
        n.params.flatMap(Converter.fromJson[Byte](_).toOption).foreach { byte =>
          StandardMain.exchange.channelForName(callback.name) match {
            case Some(nc: NetworkChannel) => nc.write(byte)
            case _                        =>
          }
        }
    }
}
