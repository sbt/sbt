import java.net.URL

object Helpers {
  def generatePomExtra(scmUrl: String, scmConnection: String,
                       developerId: String, developerName: String): xml.NodeSeq =
    <scm>
      <url>{ scmUrl }</url>
      <connection>{ scmConnection }</connection>
    </scm>
    <developers>
      <developer>
        <id>{ developerId }</id>
        <name>{ developerName }</name>
      </developer>
    </developers>
}