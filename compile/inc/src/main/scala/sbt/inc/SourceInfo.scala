package sbt
package inc

import xsbti.Problem

import java.io.File

trait SourceInfo {
  def reportedProblems: Seq[Problem]
  def unreportedProblems: Seq[Problem]
}
trait SourceInfos {
  def ++(o: SourceInfos): SourceInfos
  def add(file: File, info: SourceInfo): SourceInfos
  def --(files: Iterable[File]): SourceInfos
  def groupBy[K](f: (File) => K): Map[K, SourceInfos]
  def get(file: File): SourceInfo
  def allInfos: Map[File, SourceInfo]
}
object SourceInfos {
  def empty: SourceInfos = make(Map.empty)
  def make(m: Map[File, SourceInfo]): SourceInfos = new MSourceInfos(m)

  val emptyInfo: SourceInfo = makeInfo(Nil, Nil)
  def makeInfo(reported: Seq[Problem], unreported: Seq[Problem]): SourceInfo =
    new MSourceInfo(reported, unreported)
  def merge(infos: Traversable[SourceInfos]): SourceInfos = (SourceInfos.empty /: infos)(_ ++ _)
}
private final class MSourceInfos(val allInfos: Map[File, SourceInfo]) extends SourceInfos {
  def ++(o: SourceInfos) = new MSourceInfos(allInfos ++ o.allInfos)
  def --(sources: Iterable[File]) = new MSourceInfos(allInfos -- sources)
  def groupBy[K](f: File => K): Map[K, SourceInfos] = allInfos groupBy (x => f(x._1)) map { x =>
    (x._1, new MSourceInfos(x._2))
  }
  def add(file: File, info: SourceInfo) = new MSourceInfos(allInfos + ((file, info)))
  def get(file: File) = allInfos.getOrElse(file, SourceInfos.emptyInfo)
}
private final class MSourceInfo(val reportedProblems: Seq[Problem], val unreportedProblems: Seq[Problem])
    extends SourceInfo
