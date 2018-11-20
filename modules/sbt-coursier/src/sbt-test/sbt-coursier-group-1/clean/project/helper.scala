package coursier

object Helper {

  def checkEmpty(): Boolean =
    coursier.sbtcoursier.SbtCoursierCache.default.isEmpty

}