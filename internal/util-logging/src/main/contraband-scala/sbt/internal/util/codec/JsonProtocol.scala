/**
 * This code is generated using sbt-datatype.
 */

// DO NOT EDIT MANUALLY
package sbt.internal.util.codec
trait JsonProtocol extends sjsonnew.BasicJsonProtocol
  with sbt.internal.util.codec.ChannelLogEntryFormats
  with sbt.internal.util.codec.AbstractEntryFormats
object JsonProtocol extends JsonProtocol