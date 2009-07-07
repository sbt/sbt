package d

import org.scalacheck._

class ResourcesTest extends Properties("Resources")
{
	specify("load main resources ok", (a: Boolean) => { b.ScalaC.loadResources(); true })
	specify("load test resources ok", (a: Boolean) => { ScalaD.loadResources(); true })
}
object ScalaD
{
	def loadResources()
	{
		resource("/test-resource")
		resource("test-resource-c")
		resource("/c/test-resource-c")
	}
	def resource(s: String) = assert(getClass.getResource(s) != null, "Could not find resource '" + s + "'")
}