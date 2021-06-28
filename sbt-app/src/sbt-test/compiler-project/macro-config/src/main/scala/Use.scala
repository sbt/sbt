object Use
{
	def main(args: Array[String])
	{
		val str = Def.desugar(3 + 5)
		assert(str == args(0), s"Expected '${args(0)}', got '$str'")
	}
}