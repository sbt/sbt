import sbt._
import Keys._

object HouseRulesPlugin extends AutoPlugin {
  override def requires = plugins.JvmPlugin
  override def trigger = allRequirements

  override def projectSettings: Seq[Def.Setting[_]] = baseSettings

  lazy val baseSettings: Seq[Def.Setting[_]] = Seq(
    scalacOptions ++= Seq("-encoding", "utf8"),
    scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked"),
    scalacOptions += "-language:higherKinds",
    scalacOptions += "-language:implicitConversions",
    scalacOptions ++= "-Xfuture".ifScala213OrMinus.value.toList,
    scalacOptions ++= "-Xlint".ifScala2.value.toList,
    // scalacOptions ++= "-Xfatal-warnings"
    //   .ifScala3x(_ => {
    //     sys.props.get("sbt.build.fatal") match {
    //       case Some(_) => java.lang.Boolean.getBoolean("sbt.build.fatal")
    //       case _       => true
    //     }
    //   })
    //   .value
    //   .toList,
    scalacOptions ++= "-Ykind-projector".ifScala3.value.toList,
    scalacOptions ++= "-Yinline-warnings".ifScala211OrMinus.value.toList,
    scalacOptions ++= "-Yno-adapted-args".ifScala212OrMinus.value.toList,
    scalacOptions ++= "-Ywarn-dead-code".ifScala2.value.toList,
    scalacOptions ++= "-Ywarn-numeric-widen".ifScala2.value.toList,
    scalacOptions ++= "-Ywarn-value-discard".ifScala2.value.toList,
    scalacOptions ++= "-Ywarn-unused-import".ifScala2x(v => 11 <= v && v <= 12).value.toList,
    scalacOptions ++= {
      scalaPartV.value match {
        case Some((3, _)) => Seq("-Wunused:imports,implicits") // ,nowarn
        case Some((2, _)) => Seq("-Ywarn-unused:-privates,-locals,-explicits")
        case _            => Seq.empty
      }
    },
    scalacOptions ++= "-Xsource:3".ifScala2.value.toList
  ) ++ Seq(Compile, Test).flatMap(c =>
    (c / console / scalacOptions) --= Seq("-Ywarn-unused-import", "-Xlint")
  )

  private def scalaPartV = Def setting (CrossVersion partialVersion scalaVersion.value)

  private implicit final class AnyWithIfScala[A](val __x: A) {
    def ifScala2x(p: Long => Boolean) =
      Def.setting(scalaPartV.value.collect { case (2, y) if p(y) => __x })
    def ifScala3x(p: Long => Boolean) =
      Def.setting(scalaPartV.value.collect { case (3, y) if p(y) => __x })
    def ifScalaLte(v: Long) = ifScala2x(_ <= v)
    def ifScalaGte(v: Long) = ifScala2x(_ >= v)
    def ifScala211OrMinus = ifScalaLte(11)
    def ifScala211OrPlus = ifScalaGte(11)
    def ifScala212OrMinus = ifScalaLte(12)
    def ifScala213OrMinus = ifScalaLte(13)
    def ifScala2 = ifScala2x(_ => true)
    def ifScala3 = Def.setting(
      if (scalaBinaryVersion.value == "3") Seq(__x)
      else Nil
    )
  }
}
