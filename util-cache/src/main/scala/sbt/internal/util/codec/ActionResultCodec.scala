/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.util.codec
trait ActionResultCodec
    extends sbt.internal.util.codec.HashedVirtualFileRefFormats
    with sbt.internal.util.codec.ByteBufferFormats
    with sjsonnew.BasicJsonProtocol
    with sbt.internal.util.codec.ActionResultFormats
object ActionResultCodec extends ActionResultCodec
