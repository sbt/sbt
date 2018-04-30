
object ScalaVersion {

  def scala212 = "2.12.6"
  def scala211 = "2.11.12"
  def scala210 = "2.10.7"

  val versions = Seq(scala212, scala211, scala210)

  val map = versions
    .map { v =>
      v.split('.').take(2).mkString(".") -> v
    }
    .toMap

}
