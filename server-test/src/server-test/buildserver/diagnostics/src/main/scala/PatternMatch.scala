
class PatternMatch {
  val opt: Option[Int] = None
  opt match {
    case Some(value) => ()
  }
}
