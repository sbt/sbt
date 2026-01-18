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
  with sbt.internal.worker.codec.StringURIFormats
  with sbt.internal.worker.codec.FileConverterConfigFormats
  with sbt.internal.util.codec.HashedVirtualFileRefFormats
  with sbt.internal.worker.codec.HVFRURIFormats
  with sbt.internal.worker.codec.CompileConfigFormats
  with sbt.internal.worker.codec.CompileResponseFormats
object JsonProtocol extends JsonProtocol