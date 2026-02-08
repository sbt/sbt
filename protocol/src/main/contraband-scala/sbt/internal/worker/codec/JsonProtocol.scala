/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.worker.codec
trait JsonProtocol extends sjsonnew.BasicJsonProtocol
  with sbt.internal.worker.codec.FilePathFormats
  with sbt.internal.worker.codec.JvmRunInfoFormats
  with sbt.internal.worker.codec.NativeRunInfoFormats
  with sbt.internal.worker.codec.RunInfoFormats
  with sbt.internal.worker.codec.ClientJobParamsFormats
  with sbt.internal.worker.codec.ScalaInstanceConfigFormats
object JsonProtocol extends JsonProtocol