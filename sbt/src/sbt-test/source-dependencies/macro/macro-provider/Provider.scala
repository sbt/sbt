package macro

object Provider {
	def macro tree(args: Any) = reify(args)
}
