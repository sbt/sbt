package b

class ScalaB
{
	def decrement(i: Int) = i - 1
}
object ScalaC
{
	def loadResources()
	{
		resource("/main-resource")
		resource("main-resource-a")
		resource("/a/main-resource-a")
	}
	def resource(s: String) = assert(getClass.getResource(s) != null, "Could not find resource '" + s + "'")
}