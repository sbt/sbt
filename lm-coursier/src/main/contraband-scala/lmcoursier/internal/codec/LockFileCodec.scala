/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package lmcoursier.internal.codec
trait LockFileCodec extends sjsonnew.BasicJsonProtocol
  with lmcoursier.internal.codec.ArtifactLockFormats
  with lmcoursier.internal.codec.DependencyLockFormats
  with lmcoursier.internal.codec.ConfigurationLockFormats
  with lmcoursier.internal.codec.InstantFormats
  with lmcoursier.internal.codec.LockFileMetadataFormats
  with lmcoursier.internal.codec.LockFileDataFormats
object LockFileCodec extends LockFileCodec