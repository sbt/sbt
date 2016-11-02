package sbt

package object librarymanagement extends ResolversSyntax {
  type ExclusionRule = InclExclRule
  val ExclusionRule = InclExclRule

  type InclusionRule = InclExclRule
  val InclusionRule = InclExclRule
}
