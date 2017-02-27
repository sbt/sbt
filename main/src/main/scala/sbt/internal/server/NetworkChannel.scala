/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.typesafe.com>
 */
package sbt
package internal
package server

import java.net.{ Socket, SocketTimeoutException, URI }
import java.util.concurrent.atomic.AtomicBoolean
import scala.util.{ Left, Right }
import sbt.protocol._
import sjsonnew._

final class NetworkChannel(val name: String, connection: Socket, structure: BuildStructure, currentBuild: URI) extends CommandChannel {
  private val running = new AtomicBoolean(true)
  private val delimiter: Byte = '\n'.toByte
  private val out = connection.getOutputStream

  val thread = new Thread(s"sbt-networkchannel-${connection.getPort}") {
    override def run(): Unit = {
      try {
        val readBuffer = new Array[Byte](4096)
        val in = connection.getInputStream
        connection.setSoTimeout(5000)
        var buffer: Vector[Byte] = Vector.empty
        var bytesRead = 0
        while (bytesRead != -1 && running.get) {
          try {
            bytesRead = in.read(readBuffer)
            buffer = buffer ++ readBuffer.toVector.take(bytesRead)
            // handle un-framing
            val delimPos = buffer.indexOf(delimiter)
            if (delimPos > 0) {
              val chunk = buffer.take(delimPos)
              buffer = buffer.drop(delimPos + 1)

              Serialization.deserializeCommand(chunk).fold(
                errorDesc => println("Got invalid chunk from client: " + errorDesc),
                onCommand
              )
            }

          } catch {
            case _: SocketTimeoutException => // its ok
          }
        }

      } finally {
        shutdown()
      }
    }
  }
  thread.start()

  def publishEvent[A: JsonFormat](event: A): Unit =
    {
      val bytes = Serialization.serializeEvent(event)
      publishBytes(bytes)
    }

  def publishEventMessage(event: EventMessage): Unit =
    {
      val bytes = Serialization.serializeEventMessage(event)
      publishBytes(bytes)
    }

  def publishBytes(event: Array[Byte]): Unit =
    {
      out.write(event)
      out.write(delimiter.toInt)
      out.flush()
    }

  def onCommand(command: CommandMessage): Unit = command match {
    case x: ExecCommand  => onExecCommand(x)
    case x: SettingQuery => onSettingQuery(x)
  }

  private def onExecCommand(cmd: ExecCommand) =
    append(Exec(cmd.commandLine, cmd.execId orElse Some(Exec.newExecId), Some(CommandSource(name))))

  private def onSettingQuery(req: SettingQuery) = {
    import sbt.internal.util.complete.Parser

    val key = Parser.parse(req.setting, SettingQuery.scopedKeyParser(structure, currentBuild))

    def getSettingValue[A](key: Def.ScopedKey[A]) =
      structure.data.get(key.scope, key.key)
        .toRight(s"Key ${Def displayFull key} not found")
        .flatMap {
          case _: Task[_]      => Left(s"Key ${Def displayFull key} is a task, can only query settings")
          case _: InputTask[_] => Left(s"Key ${Def displayFull key} is an input task, can only query settings")
          case x               => Right(x)
        }

    val values = key match {
      case Left(msg)  => Left(s"Invalid programmatic input: $msg")
      case Right(key) => Right(getSettingValue(key))
    }

    val jsonValues = values match {
      case Left(errors) => errors
      case Right(value) => value.toString
    }

    StandardMain.exchange publishEventMessage SettingQueryResponse(jsonValues)
  }

  def shutdown(): Unit = {
    println("Shutting down client connection")
    running.set(false)
    out.close()
  }
}

object SettingQuery {
  import sbt.internal.util.{ AttributeKey, Settings }
  import sbt.internal.util.complete.{ DefaultParsers, Parser }, DefaultParsers._
  import sbt.Def.{ showBuildRelativeKey, ScopedKey }

  // Similar to Act.ParsedAxis / Act.projectRef / Act.resolveProject except you can't omit the project reference

  sealed trait ParsedExplicitAxis[+T]
  final object ParsedExplicitGlobal extends ParsedExplicitAxis[Nothing]
  final class ParsedExplicitValue[T](val value: T) extends ParsedExplicitAxis[T]
  def explicitValue[T](t: Parser[T]): Parser[ParsedExplicitAxis[T]] = t map { v => new ParsedExplicitValue(v) }

  def projectRef(index: KeyIndex, currentBuild: URI): Parser[ParsedExplicitAxis[ResolvedReference]] = {
    val global = token(Act.GlobalString ~ '/') ^^^ ParsedExplicitGlobal
    val trailing = '/' !!! "Expected '/' (if selecting a project)"
    global | explicitValue(Act.resolvedReference(index, currentBuild, trailing))
  }

  def resolveProject(parsed: ParsedExplicitAxis[ResolvedReference]): Option[ResolvedReference] = parsed match {
    case ParsedExplicitGlobal       => None
    case pv: ParsedExplicitValue[_] => Some(pv.value)
  }

  def scopedKeyFull(
    index: KeyIndex,
    currentBuild: URI,
    defaultConfigs: Option[ResolvedReference] => Seq[String],
    keyMap: Map[String, AttributeKey[_]]
  ): Parser[Seq[Parser[ParsedKey]]] = {
    for {
      rawProject <- projectRef(index, currentBuild)
      proj = resolveProject(rawProject)
      confAmb <- Act.config(index configs proj)
      partialMask = ScopeMask(true, confAmb.isExplicit, false, false)
    } yield Act.taskKeyExtra(index, defaultConfigs, keyMap, proj, confAmb, partialMask)
  }

  def scopedKeyParser(structure: BuildStructure, currentBuild: URI): Parser[ScopedKey[_]] =
    scopedKey(
      structure.index.keyIndex,
      currentBuild,
      structure.extra.configurationsForAxis,
      structure.index.keyMap,
      structure.data
    )

  def scopedKeySelected(
    index: KeyIndex,
    currentBuild: URI,
    defaultConfigs: Option[ResolvedReference] => Seq[String],
    keyMap: Map[String, AttributeKey[_]],
    data: Settings[Scope]
  ): Parser[ParsedKey] =
    scopedKeyFull(index, currentBuild, defaultConfigs, keyMap) flatMap { choices =>
      Act.select(choices, data)(showBuildRelativeKey(currentBuild, index.buildURIs.size > 1))
    }

  def scopedKey(
    index: KeyIndex,
    currentBuild: URI,
    defaultConfigs: Option[ResolvedReference] => Seq[String],
    keyMap: Map[String, AttributeKey[_]],
    data: Settings[Scope]
  ): Parser[ScopedKey[_]] =
    scopedKeySelected(index, currentBuild, defaultConfigs, keyMap, data).map(_.key)
}
