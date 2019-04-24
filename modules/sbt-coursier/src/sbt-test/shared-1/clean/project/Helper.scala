package coursier

object Helper {

  def checkEmpty(): Boolean =
    coursier.lmcoursier.internal.SbtCoursierCache.default.isEmpty

}
