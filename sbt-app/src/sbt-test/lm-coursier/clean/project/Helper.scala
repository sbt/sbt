package coursier

object Helper {

  def checkEmpty(): Boolean =
    lmcoursier.internal.SbtCoursierCache.default.isEmpty

}
