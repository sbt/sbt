/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.protocol.codec
trait JsonRPCProtocol
    extends sbt.internal.util.codec.JValueFormats
    with sjsonnew.BasicJsonProtocol
    with sbt.internal.protocol.codec.JsonRpcRequestMessageFormats
    with sbt.internal.protocol.codec.JsonRpcResponseErrorFormats
    with sbt.internal.protocol.codec.JsonRpcResponseMessageFormats
    with sbt.internal.protocol.codec.JsonRpcNotificationMessageFormats

object JsonRPCProtocol extends JsonRPCProtocol
