/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
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
