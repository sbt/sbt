/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
trait JsonProtocol extends sjsonnew.BasicJsonProtocol
  with sbt.internal.bsp.codec.BuildTargetIdentifierFormats
  with sbt.internal.util.codec.JValueFormats
  with sbt.internal.bsp.codec.BuildTargetFormats
  with sbt.internal.bsp.codec.BuildClientCapabilitiesFormats
  with sbt.internal.bsp.codec.InitializeBuildParamsFormats
  with sbt.internal.bsp.codec.InitializeBuildResultFormats
  with sbt.internal.bsp.codec.WorkspaceBuildTargetsResultFormats
  with sbt.internal.bsp.codec.SourcesParamsFormats
  with sbt.internal.bsp.codec.SourceItemFormats
  with sbt.internal.bsp.codec.SourcesItemFormats
  with sbt.internal.bsp.codec.SourcesResultFormats
  with sbt.internal.bsp.codec.ScalaBuildTargetFormats
  with sbt.internal.bsp.codec.SbtBuildTargetFormats
  with sbt.internal.bsp.codec.ScalacOptionsParamsFormats
  with sbt.internal.bsp.codec.ScalacOptionsItemFormats
  with sbt.internal.bsp.codec.ScalacOptionsResultFormats
object JsonProtocol extends JsonProtocol