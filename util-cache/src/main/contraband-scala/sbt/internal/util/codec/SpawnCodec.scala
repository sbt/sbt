/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.util.codec
trait SpawnCodec extends sjsonnew.BasicJsonProtocol
  with sbt.internal.util.codec.SpawnInputFormats
  with sbt.internal.util.codec.HashedVirtualFileRefFormats
  with sbt.internal.util.codec.SpawnExecFormats
object SpawnCodec extends SpawnCodec