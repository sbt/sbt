package coursier

object Helper {

  def checkEmpty(): Boolean =
    coursier.lmcoursier.SbtCoursierCache.default.isEmpty

}
