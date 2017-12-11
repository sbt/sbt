package sbt.librarymanagement

final case class InclExclRule(org: String = "*", name: String = "*") {
  def withOrganization(org: String): InclExclRule =
    copy(org = org)
  def withName(name: String): InclExclRule =
    copy(name = name)
}
