object B {
	def main(args: Array[String]) = assert(args(0).toInt == A.x, s"actual A.x is ${A.x}")
}
