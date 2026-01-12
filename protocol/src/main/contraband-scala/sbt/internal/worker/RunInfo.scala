/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.worker
final class RunInfo private (
  val jvm: Boolean,
  val jvmRunInfo: Option[sbt.internal.worker.JvmRunInfo],
  val nativeRunInfo: Option[sbt.internal.worker.NativeRunInfo],
  val windowTitle: Option[String]) extends Serializable {
  
  private def this(jvm: Boolean, jvmRunInfo: Option[sbt.internal.worker.JvmRunInfo], windowTitle: Option[String]) = this(jvm, jvmRunInfo, None, windowTitle)
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: RunInfo => (this.jvm == x.jvm) && (this.jvmRunInfo == x.jvmRunInfo) && (this.nativeRunInfo == x.nativeRunInfo) && (this.windowTitle == x.windowTitle)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.worker.RunInfo".##) + jvm.##) + jvmRunInfo.##) + nativeRunInfo.##) + windowTitle.##)
  }
  override def toString: String = {
    "RunInfo(" + jvm + ", " + jvmRunInfo + ", " + nativeRunInfo + ", " + windowTitle + ")"
  }
  private def copy(jvm: Boolean = jvm, jvmRunInfo: Option[sbt.internal.worker.JvmRunInfo] = jvmRunInfo, nativeRunInfo: Option[sbt.internal.worker.NativeRunInfo] = nativeRunInfo, windowTitle: Option[String] = windowTitle): RunInfo = {
    new RunInfo(jvm, jvmRunInfo, nativeRunInfo, windowTitle)
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
  def withWindowTitle(windowTitle: Option[String]): RunInfo = {
    copy(windowTitle = windowTitle)
  }
  def withWindowTitle(windowTitle: String): RunInfo = {
    copy(windowTitle = Option(windowTitle))
  }
}
object RunInfo {
  
  def apply(jvm: Boolean, jvmRunInfo: Option[sbt.internal.worker.JvmRunInfo], windowTitle: Option[String]): RunInfo = new RunInfo(jvm, jvmRunInfo, windowTitle)
  def apply(jvm: Boolean, jvmRunInfo: sbt.internal.worker.JvmRunInfo, windowTitle: String): RunInfo = new RunInfo(jvm, Option(jvmRunInfo), Option(windowTitle))
  def apply(jvm: Boolean, jvmRunInfo: Option[sbt.internal.worker.JvmRunInfo], nativeRunInfo: Option[sbt.internal.worker.NativeRunInfo], windowTitle: Option[String]): RunInfo = new RunInfo(jvm, jvmRunInfo, nativeRunInfo, windowTitle)
  def apply(jvm: Boolean, jvmRunInfo: sbt.internal.worker.JvmRunInfo, nativeRunInfo: sbt.internal.worker.NativeRunInfo, windowTitle: String): RunInfo = new RunInfo(jvm, Option(jvmRunInfo), Option(nativeRunInfo), Option(windowTitle))
}
