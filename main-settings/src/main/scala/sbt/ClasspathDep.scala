/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

enum ClasspathDep[PR <: ProjectReference]:
  case ResolvedClasspathDependency(project0: ProjectRef, configuration0: Option[String])
      extends ClasspathDep[ProjectRef]
  case ClasspathDependency(project0: ProjectReference, configuration0: Option[String])
      extends ClasspathDep[ProjectReference]

  def project: PR = this match
    case dep: ResolvedClasspathDependency => dep.project0
    case dep: ClasspathDependency         => dep.project0

  def configuration: Option[String] = this match
    case dep: ResolvedClasspathDependency => dep.configuration0
    case dep: ClasspathDependency         => dep.configuration0
