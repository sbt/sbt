package sbt
package inc

	import xsbti.Problem

	import java.io.File

trait SourceInfo
{
	def reportedProblems: Seq[Problem]
	def unreportedProblems: Seq[Problem]
}
trait SourceInfos
{
	def ++(o: SourceInfos): SourceInfos
	def add(file: File, info: SourceInfo): SourceInfos
	def --(files: Iterable[File]): SourceInfos
	def get(file: File): SourceInfo
	def allInfos: Map[File, SourceInfo]
}
object SourceInfos
{
	def empty: SourceInfos = make(Map.empty)
	def make(m: Map[File, SourceInfo]): SourceInfos = new MSourceInfos(m)

	val emptyInfo: SourceInfo = makeInfo(Nil, Nil)
	def makeInfo(reported: Seq[Problem], unreported: Seq[Problem]): SourceInfo =
		new MSourceInfo(reported, unreported)
}
private final class MSourceInfos(val allInfos: Map[File, SourceInfo]) extends SourceInfos
{
	def ++(o: SourceInfos) = new MSourceInfos(allInfos ++ o.allInfos)
	def --(sources: Iterable[File]) = new MSourceInfos(allInfos -- sources)
	def add(file: File, info: SourceInfo) = new MSourceInfos(allInfos + ((file, info)))
	def get(file:File) = allInfos.getOrElse(file, SourceInfos.emptyInfo)
}
private final class MSourceInfo(val reportedProblems: Seq[Problem], val unreportedProblems: Seq[Problem]) extends SourceInfo
