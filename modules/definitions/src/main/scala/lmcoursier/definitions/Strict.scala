package lmcoursier.definitions

import dataclass._

@data class Strict(
  include: Set[(String, String)] = Set(("*", "*")),
  exclude: Set[(String, String)] = Set.empty,
  ignoreIfForcedVersion: Boolean = true,
  @since
  includeByDefault: Boolean = false,
  semVer: Boolean = false
) {
  def addInclude(include: (String, String)*): Strict =
    withInclude(this.include ++ include)
  def addExclude(exclude: (String, String)*): Strict =
    withExclude(this.exclude ++ exclude)
}
