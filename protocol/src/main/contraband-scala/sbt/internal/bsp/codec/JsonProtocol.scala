/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
trait JsonProtocol extends sjsonnew.BasicJsonProtocol
  with sbt.internal.bsp.codec.BuildTargetIdentifierFormats
  with sbt.internal.bsp.codec.BuildTargetCapabilitiesFormats
  with sbt.internal.util.codec.JValueFormats
  with sbt.internal.bsp.codec.BuildTargetFormats
  with sbt.internal.bsp.codec.DebugSessionAddressFormats
  with sbt.internal.bsp.codec.DebugSessionParamsFormats
  with sbt.internal.bsp.codec.TaskIdFormats
  with sbt.internal.bsp.codec.TextDocumentIdentifierFormats
  with sbt.internal.bsp.codec.PositionFormats
  with sbt.internal.bsp.codec.RangeFormats
  with sbt.internal.bsp.codec.DiagnosticFormats
  with sbt.internal.bsp.codec.BuildClientCapabilitiesFormats
  with sbt.internal.bsp.codec.InitializeBuildParamsFormats
  with sbt.internal.bsp.codec.CompileProviderFormats
  with sbt.internal.bsp.codec.TestProviderFormats
  with sbt.internal.bsp.codec.RunProviderFormats
  with sbt.internal.bsp.codec.BuildServerCapabilitiesFormats
  with sbt.internal.bsp.codec.InitializeBuildResultFormats
  with sbt.internal.bsp.codec.PublishDiagnosticsParamsFormats
  with sbt.internal.bsp.codec.WorkspaceBuildTargetsResultFormats
  with sbt.internal.bsp.codec.SourcesParamsFormats
  with sbt.internal.bsp.codec.SourceItemFormats
  with sbt.internal.bsp.codec.SourcesItemFormats
  with sbt.internal.bsp.codec.SourcesResultFormats
  with sbt.internal.bsp.codec.DependencySourcesParamsFormats
  with sbt.internal.bsp.codec.DependencySourcesItemFormats
  with sbt.internal.bsp.codec.DependencySourcesResultFormats
  with sbt.internal.bsp.codec.TaskStartParamsFormats
  with sbt.internal.bsp.codec.TaskFinishParamsFormats
  with sbt.internal.bsp.codec.CompileParamsFormats
  with sbt.internal.bsp.codec.BspCompileResultFormats
  with sbt.internal.bsp.codec.CompileTaskFormats
  with sbt.internal.bsp.codec.CompileReportFormats
  with sbt.internal.bsp.codec.TestParamsFormats
  with sbt.internal.bsp.codec.TestResultFormats
  with sbt.internal.bsp.codec.RunParamsFormats
  with sbt.internal.bsp.codec.RunResultFormats
  with sbt.internal.bsp.codec.ScalaBuildTargetFormats
  with sbt.internal.bsp.codec.SbtBuildTargetFormats
  with sbt.internal.bsp.codec.ScalacOptionsParamsFormats
  with sbt.internal.bsp.codec.ScalacOptionsItemFormats
  with sbt.internal.bsp.codec.ScalacOptionsResultFormats
  with sbt.internal.bsp.codec.BspConnectionDetailsFormats
  with sbt.internal.bsp.codec.MetalsMetadataFormats
  with sbt.internal.bsp.codec.ScalaTestClassesItemFormats
  with sbt.internal.bsp.codec.ScalaTestParamsFormats
  with sbt.internal.bsp.codec.ScalaTestClassesParamsFormats
  with sbt.internal.bsp.codec.ScalaTestClassesResultFormats
  with sbt.internal.bsp.codec.ScalaMainClassesParamsFormats
  with sbt.internal.bsp.codec.ScalaMainClassFormats
  with sbt.internal.bsp.codec.ScalaMainClassesItemFormats
  with sbt.internal.bsp.codec.ScalaMainClassesResultFormats
  with sbt.internal.bsp.codec.ResourcesParamsFormats
  with sbt.internal.bsp.codec.ResourcesItemFormats
  with sbt.internal.bsp.codec.ResourcesResultFormats
object JsonProtocol extends JsonProtocol