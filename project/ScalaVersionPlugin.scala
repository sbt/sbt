
import sbt.complete.Parser
import sbt.Keys.commands
import sbt.{AutoPlugin, Command}

object ScalaVersionPlugin extends AutoPlugin {

  override def trigger = allRequirements

  override def buildSettings = Seq(
    commands ++= ScalaVersion.map.toSeq.map {
      case (sbv, sv) =>
        Command(s"scala${sbv.filter(_ != '.')}")(_ => Parser.success(())) { (st, _) =>
          val cmd0 = s"++$sv!"
          Parser.parse(cmd0, st.combinedParser) match {
            case Right(cmd) => cmd()
            case Left(msg) => throw sys.error(s"Invalid command: $cmd0\n$msg")
          }
        }
    },
    commands += Command("scalaFromEnv")(_ => Parser.success(())) { (st, _) =>
      val sv = sys.env.get("SCALA_VERSION") match {
        case None =>
          throw new Exception("SCALA_VERSION not set")
        case Some(s) if s.count(_ == '.') == 1 =>
          ScalaVersion.map.getOrElse(
            s,
            throw new Exception(
              s"No scala version found for binary version $s" +
                s" (available scala versions: ${ScalaVersion.versions.mkString(", ")})"
            )
          )
        case Some(s) =>
          s
      }
      val cmd0 = s"++$sv!"
      Parser.parse(cmd0, st.combinedParser) match {
        case Right(cmd) => cmd()
        case Left(msg) => throw sys.error(s"Invalid command: $cmd0\n$msg")
      }
    }
  )

}
