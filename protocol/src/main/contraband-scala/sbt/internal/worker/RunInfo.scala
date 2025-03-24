/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.worker
final class RunInfo private (
  val jvm: Boolean,
  val jvmRunInfo: Option[sbt.internal.worker.JvmRunInfo],
  val nativeRunInfo: Option[sbt.internal.worker.NativeRunInfo]) extends Serializable {
  
  private def this(jvm: Boolean, jvmRunInfo: Option[sbt.internal.worker.JvmRunInfo]) = this(jvm, jvmRunInfo, None)
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: RunInfo => (this.jvm == x.jvm) && (this.jvmRunInfo == x.jvmRunInfo) && (this.nativeRunInfo == x.nativeRunInfo)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "sbt.internal.worker.RunInfo".##) + jvm.##) + jvmRunInfo.##) + nativeRunInfo.##)
  }
  override def toString: String = {
    "RunInfo(" + jvm + ", " + jvmRunInfo + ", " + nativeRunInfo + ")"
  }
  private def copy(jvm: Boolean = jvm, jvmRunInfo: Option[sbt.internal.worker.JvmRunInfo] = jvmRunInfo, nativeRunInfo: Option[sbt.internal.worker.NativeRunInfo] = nativeRunInfo): RunInfo = {
    new RunInfo(jvm, jvmRunInfo, nativeRunInfo)
  }
  def withJvm(jvm: Boolean): RunInfo = {
    copy(jvm = jvm)
  }
  def withJvmRunInfo(jvmRunInfo: Option[sbt.internal.worker.JvmRunInfo]): RunInfo = {
    copy(jvmRunInfo = jvmRunInfo)
  }
  def withJvmRunInfo(jvmRunInfo: sbt.internal.worker.JvmRunInfo): RunInfo = {
    copy(jvmRunInfo = Option(jvmRunInfo))
  }
  def withNativeRunInfo(nativeRunInfo: Option[sbt.internal.worker.NativeRunInfo]): RunInfo = {
    copy(nativeRunInfo = nativeRunInfo)
  }
  def withNativeRunInfo(nativeRunInfo: sbt.internal.worker.NativeRunInfo): RunInfo = {
    copy(nativeRunInfo = Option(nativeRunInfo))
  }
}
object RunInfo {
  
  def apply(jvm: Boolean, jvmRunInfo: Option[sbt.internal.worker.JvmRunInfo]): RunInfo = new RunInfo(jvm, jvmRunInfo)
  def apply(jvm: Boolean, jvmRunInfo: sbt.internal.worker.JvmRunInfo): RunInfo = new RunInfo(jvm, Option(jvmRunInfo))
  def apply(jvm: Boolean, jvmRunInfo: Option[sbt.internal.worker.JvmRunInfo], nativeRunInfo: Option[sbt.internal.worker.NativeRunInfo]): RunInfo = new RunInfo(jvm, jvmRunInfo, nativeRunInfo)
  def apply(jvm: Boolean, jvmRunInfo: sbt.internal.worker.JvmRunInfo, nativeRunInfo: sbt.internal.worker.NativeRunInfo): RunInfo = new RunInfo(jvm, Option(jvmRunInfo), Option(nativeRunInfo))
}
