/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.util.codec
trait ManifestCodec extends sbt.internal.util.codec.HashedVirtualFileRefFormats
  with sjsonnew.BasicJsonProtocol
  with sbt.internal.util.codec.ManifestFormats
object ManifestCodec extends ManifestCodec