package sbt

package object librarymanagement {
  type ExclusionRule = InclExclRule
  val ExclusionRule = InclExclRule

  type InclusionRule = InclExclRule
  val InclusionRule = InclExclRule
}
