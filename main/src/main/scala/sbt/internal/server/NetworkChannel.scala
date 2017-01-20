/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.typesafe.com>
 */
package sbt
package internal
package server

import java.net.{ Socket, SocketTimeoutException }
import java.util.concurrent.atomic.AtomicBoolean
import scala.util.{ Left, Right }
import sbt.protocol._
import sjsonnew._, LList.:*:

final class NetworkChannel(val name: String, connection: Socket, state: State) extends CommandChannel {
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

    val extracted = Project extract state
    val keys = Parser.parse(req.setting, Act aggregatedKeyParser extracted)

    def getSettingValue[A](key: Def.ScopedKey[A]) =
      extracted.structure.data.get(key.scope, key.key)
        .toRight(s"Key ${Def displayFull key} not found")
        .flatMap {
          case _: Task[_]      => Left(s"Key ${Def displayFull key} is a task, can only query settings")
          case _: InputTask[_] => Left(s"Key ${Def displayFull key} is an input task, can only query settings")
          case x               => Right(x)
        }

    def zeroValues: Either[Vector[String], Vector[Any]] = Right(Vector.empty)
    def anyLeftsOrAllRights[A, B](acc: Either[Vector[A], Vector[B]], elem: Either[A, B]): Either[Vector[A], Vector[B]] =
      (acc, elem) match {
        case (Right(a), Right(x)) => Right(a :+ x)
        case (Right(_), Left(x))  => Left(Vector(x))
        case (Left(a), Right(_))  => Left(a)
        case (Left(a), Left(x))   => Left(a :+ x)
      }

    val values = keys match {
      case Left(msg)   => Left(s"Invalid programmatic input:" +: (msg.lines.toVector map ("   " + _)))
      case Right(keys) => keys.map(getSettingValue(_)).foldLeft(zeroValues)(anyLeftsOrAllRights)
    }

    val jsonValues = values match {
      case Left(errors)  => errors
      case Right(values) => values map (_.toString)
    }

    StandardMain.exchange publishEventMessage SettingQueryResponse(jsonValues)
  }

  def shutdown(): Unit = {
    println("Shutting down client connection")
    running.set(false)
    out.close()
  }
}

trait SettingQueryInstances {
  import BasicJsonProtocol._

  type SettingQueryRepr = String :*: LNil
  implicit def settingQueryIso: IsoLList.Aux[SettingQuery, SettingQueryRepr] = LList.iso(
    (x => "settingKey" -> x.setting :*: LNil),
    (x => SettingQuery(x.head))
  )

  import sbt.internal.util._

  type AttrKeyRepr[A] = String :*: Manifest[A] :*: Option[String] :*: Vector[AttributeKey[_]] :*: Boolean :*: Int :*: LNil

  // FIXME: Can't go this IsoLList way because AttributeKey depends on AttributeKey (extend)
  implicit def attrKeyIso[A]: IsoLList.Aux[AttributeKey[A], AttrKeyRepr[A]] = ???
  //    LList.iso[AttributeKey[A], AttrKeyRepr[A]](attrKeyToRepr, attrKeyFromRepr)

  //  def attrKeyToRepr[A](x: AttributeKey[A]): AttrKeyRepr[A] = (
  //    /*   */ "label" -> x.label /*          */ :*:
  //    /**/ "manifest" -> x.manifest /*       */ :*:
  //    /*    */ "desc" -> x.description /*    */ :*:
  //    /*  */ "extend" -> x.extend.toVector /**/ :*:
  //    /* */ "isLocal" -> x.isLocal /*        */ :*:
  //    /*    */ "rank" -> x.rank /*           */ :*:
  //    LNil
  //  )

  def attrKeyFromRepr[A](x: AttrKeyRepr[A]): AttributeKey[A] = {
    val LCons("label", label,
      LCons("manifest", manifest,
        LCons("desc", desc,
          LCons("extend", extend,
            LCons("isLabel", isLocal,
              LCons("rank", rank, LNil)
              ))))) = x
    if (isLocal) AttributeKey.local[A](manifest)
    else desc match {
      case Some(desc) => AttributeKey(label, desc, extend, rank)(manifest)
      case None => extend match {
        case Seq() => AttributeKey(label, rank)(manifest)
        case _ =>
          // With the given API it's not possible to create an AttributeKey
          // which extends other attribute keys without having a description
          // But that's not enforced in the data types. So default description to ""
          AttributeKey(label, "", extend, rank)(manifest)
      }
    }
  }

  // TODO: or use AttributeKey label? (String)
  //  implicit def attrMapFormat: JsonFormat[AttributeMap] = project[AttributeMap, Map[AttributeKey[_], Any]](
  //    attrMap => attrMap.entries.iterator.map(x => x.key -> x.value).toMap,
  //    map => AttributeMap(map.iterator.map { case (k: AttributeKey[kt], v) => AttributeEntry(k, v.asInstanceOf[kt]) }.toSeq)
  //  )

  implicit def scopeAxisIso[A](implicit z: JsonFormat[A]): JsonFormat[ScopeAxis[A]] =
    new JsonFormat[ScopeAxis[A]] {
      def write[J](obj: ScopeAxis[A], builder: Builder[J]): Unit = obj match {
        case This      => builder writeString "This"
        case Global    => builder writeString "Global"
        case Select(s) => z.write(s, builder)
      }
      def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): ScopeAxis[A] = jsOpt match {
        case None           => deserializationError("Expected some JSON but found None")
        case Some("This")   => This
        case Some("Global") => Global
        case Some(_)        => Select(z.read(jsOpt, unbuilder))
      }
    }

  type ScopeRepr = ScopeAxis[Reference] :*: ScopeAxis[ConfigKey] :*: ScopeAxis[AttributeKey[_]] :*: ScopeAxis[AttributeMap] :*: LNil
  //  implicit def scopeIso: IsoLList.Aux[Scope, ScopeRepr] = LList.iso[Scope, ScopeRepr](
  //    { x: Scope => "project" -> x.project :*: "config" -> x.config :*: "task" -> x.task :*: "extra" -> x.extra :*: LNil },
  //    { x: ScopeRepr => Scope(x.head, x.tail.head, x.tail.tail.head, x.tail.tail.tail.head) }
  //  )

  type SettingKeyRepr[A] = Scope :*: AttributeKey[A] :*: LNil
  //  implicit def settingKeyIso[A]: IsoLList.Aux[SettingKey[A], SettingKeyRepr[A]] = LList.iso(
  //    { x: SettingKey[A] => "scope" -> x.scope :*: "attrKey" -> x.key :*: LNil },
  //    { x: SettingKeyRepr[A] => Scoped.scopedSetting(x.head, x.tail.head) }
  //  )
}
