package lmcoursier.definitions

final class Strict private (
  val include: Set[(String, String)],
  val exclude: Set[(String, String)],
  val ignoreIfForcedVersion: Boolean
) {

  override def equals(obj: Any): Boolean =
    obj match {
      case other: Strict =>
        include == other.include &&
          exclude == other.exclude &&
          ignoreIfForcedVersion == other.ignoreIfForcedVersion
      case _ =>
        false
    }

  override def hashCode(): Int = {
    var code = 17 + "lmcoursier.definitions.Strict".##
    code = 37 * code + include.##
    code = 37 * code + exclude.##
    code = 37 * code + ignoreIfForcedVersion.##
    37 * code
  }

  override def toString: String =
    s"Strict($include, $exclude, $ignoreIfForcedVersion)"

  private def copy(
    include: Set[(String, String)] = include,
    exclude: Set[(String, String)] = exclude,
    ignoreIfForcedVersion: Boolean = ignoreIfForcedVersion
  ): Strict =
    new Strict(include, exclude, ignoreIfForcedVersion)


  def withInclude(include: Set[(String, String)]): Strict =
    copy(include = include)
  def addInclude(include: (String, String)*): Strict =
    copy(include = this.include ++ include)

  def withExclude(exclude: Set[(String, String)]): Strict =
    copy(exclude = exclude)
  def addExclude(exclude: (String, String)*): Strict =
    copy(exclude = this.exclude ++ exclude)

  def withIgnoreIfForcedVersion(ignoreIfForcedVersion: Boolean): Strict =
    copy(ignoreIfForcedVersion = ignoreIfForcedVersion)
}

object Strict {
  def apply(): Strict =
    new Strict(Set(("*", "*")), Set.empty, ignoreIfForcedVersion = true)
}
