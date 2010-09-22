package jartest

object Main
{
	def main(args: Array[String])
	{
		if(getClass.getResource("main_resource_test") == null)
			System.exit(1)
	}
}