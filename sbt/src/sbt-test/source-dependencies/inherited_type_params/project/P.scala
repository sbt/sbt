import sbt._

class P(info: ProjectInfo) extends DefaultProject(info)
{
	override def name = "test"
	lazy val checkSame = compile map { analysis =>
		analysis.apis.internal foreach { case (_, api) =>
			assert( xsbt.api.APIUtil.verifyTypeParameters(api) )
			assert( xsbt.api.SameAPI(api, api) )
		}
	}
}