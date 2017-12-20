/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

sealed trait DelegateIndex {
  def project(ref: ProjectRef): Seq[ScopeAxis[ResolvedReference]]
  def config(ref: ProjectRef, conf: ConfigKey): Seq[ScopeAxis[ConfigKey]]
  //	def task(ref: ProjectRef, task: ScopedKey[_]): Seq[ScopeAxis[ScopedKey[_]]]
  //	def extra(ref: ProjectRef, e: AttributeMap): Seq[ScopeAxis[AttributeMap]]
}
private final class DelegateIndex0(refs: Map[ProjectRef, ProjectDelegates]) extends DelegateIndex {
  def project(ref: ProjectRef): Seq[ScopeAxis[ResolvedReference]] = refs.get(ref) match {
    case Some(pd) => pd.refs; case None => Nil
  }
  def config(ref: ProjectRef, conf: ConfigKey): Seq[ScopeAxis[ConfigKey]] =
    refs.get(ref) match {
      case Some(pd) =>
        pd.confs.get(conf) match {
          case Some(cs) => cs; case None => Select(conf) :: Zero :: Nil
        }
      case None => Select(conf) :: Zero :: Nil
    }
}
private final class ProjectDelegates(val ref: ProjectRef,
                                     val refs: Seq[ScopeAxis[ResolvedReference]],
                                     val confs: Map[ConfigKey, Seq[ScopeAxis[ConfigKey]]])
