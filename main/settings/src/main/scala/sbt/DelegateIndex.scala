package sbt

sealed trait DelegateIndex
{
	def config(ref: ResolvedReference, conf: ConfigKey): Seq[ScopeAxis[ConfigKey]]
//	def task(ref: ProjectRef, task: ScopedKey[_]): Seq[ScopeAxis[ScopedKey[_]]]
//	def extra(ref: ProjectRef, e: AttributeMap): Seq[ScopeAxis[AttributeMap]]
}
private final class DelegateIndex0(refs: Map[ResolvedReference, ProjectDelegates]) extends DelegateIndex
{
	def config(ref: ResolvedReference, conf: ConfigKey): Seq[ScopeAxis[ConfigKey]] =
		refs.get(ref) match {
			case Some(pd) => pd.confs.get(conf) match { case Some(cs) => cs; case None => Select(conf) :: Global :: Nil }
			case None => Select(conf) :: Global :: Nil
		}
}
private final class ProjectDelegates(val confs: Map[ConfigKey, Seq[ScopeAxis[ConfigKey]]])