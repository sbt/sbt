import sbt._

class Test(info: ProjectInfo) extends DefaultProject(info)
{
	lazy val FirstDefault = "a"
	lazy val SecondDefault = "b"
	lazy val FirstSet = "set-first"
	lazy val SecondSet = "set-second"

	lazy val extra = new BasicEnvironment
	{
		def log = Test.this.log
		def envBackingPath = info.builderPath / "extra.properties"

		lazy val firstProperty = propertyOptional[String](FirstDefault)
		lazy val secondProperty = propertyOptional[String](SecondDefault)
	}

	import extra.{firstProperty, secondProperty}

	lazy val checkFirstUnset = checkTask(firstProperty, FirstDefault, "first.property")
	lazy val checkFirstSet = checkTask(firstProperty, FirstSet, "first.property")
	lazy val checkSecondUnset = checkTask(secondProperty, SecondDefault, "second.property")
	lazy val checkSecondSet = checkTask(secondProperty, SecondSet, "second.property")

	def checkTask[T](property: extra.Property[T], expected: T, name: String): Task =
		task { if(property.value == expected) None else Some("Expected "+name+" to be '" + expected + "', was: " + property.value) }

}