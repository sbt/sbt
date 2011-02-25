package d

	import org.scalacheck._
	import Prop._

class ResourcesTest extends Properties("Resources")
{
	property("load main resources ok") = forAll( (a: Boolean) => { b.ScalaC.loadResources(); true })
	property("load test resources ok") = forAll( (a: Boolean) => { ScalaD.loadResources(); true })
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