object B
{
	def main(args: Array[String])
	{
		val expected = args(0).toInt
		val actual = A.x
		println("Expected: " + expected)
		println("Actual: " + actual)
		assert(expected == actual, "Expected " + expected + ", got: " + actual)
	}
}
