package sbt

sealed trait SourcePosition

sealed trait FilePosition {
	def path: String
	def startLine: Int
}

case object NoPosition extends SourcePosition

final case class LinePosition(path: String, startLine: Int) extends SourcePosition with FilePosition

final case class LineRange(start: Int, end: Int) {
	def shift(n: Int) = new LineRange(start + n, end + n)
}

final case class RangePosition(path: String, range: LineRange) extends SourcePosition with FilePosition {
	def startLine = range.start
}
