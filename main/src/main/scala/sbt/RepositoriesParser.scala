package sbt

import java.io.File
import java.net.URL

import scala.io.Source
import sbt.complete.Parser
import sbt.complete.DefaultParsers._

private[sbt] object RepositoriesParser {

  private case class AfterPattern(artifactPattern: Option[String], flags: Int)
  final case class PredefinedRepository(override val id: xsbti.Predefined) extends xsbti.PredefinedRepository
  final case class MavenRepository(override val id: String, override val url: URL) extends xsbti.MavenRepository
  final case class IvyRepository(override val id: String, override val url: URL, override val ivyPattern: String,
    override val artifactPattern: String, override val mavenCompatible: Boolean, override val skipConsistencyCheck: Boolean,
    override val descriptorOptional: Boolean, val bootOnly: Boolean) extends xsbti.IvyRepository

  // Predefined repositories
  def local: Parser[xsbti.Repository] = "local" ^^^ new PredefinedRepository(xsbti.Predefined.Local)
  def mavenLocal: Parser[xsbti.Repository] = "maven-local" ^^^ new PredefinedRepository(xsbti.Predefined.MavenLocal)
  def mavenCentral: Parser[xsbti.Repository] = "maven-central" ^^^ new PredefinedRepository(xsbti.Predefined.MavenCentral)
  def predefinedResolver: Parser[xsbti.Repository] = local | mavenLocal | mavenCentral

  // Options
  def descriptorOptional: Parser[Int] = "descriptorOptional" ^^^ Flags.descriptorOptionalFlag
  def skipConsistencyCheck: Parser[Int] = "skipConsistencyCheck" ^^^ Flags.skipConsistencyCheckFlag
  def bootOnly: Parser[Int] = "bootOnly" ^^^ Flags.bootOnlyFlag
  def mavenCompatible: Parser[Int] = "mavenCompatible" ^^^ Flags.mavenCompatibleFlag

  def option: Parser[Int] = descriptorOptional | skipConsistencyCheck | bootOnly | mavenCompatible
  def options: Parser[Int] = rep1sep(option, separator) map (_ reduce (_ | _))

  def name: Parser[String] = ID
  def separator: Parser[String] = "," ~> charClass(c => c == ' ' || c == '\t').*.string
  def nonComma: Parser[String] = charClass(_ != ',').*.string
  def ivyPattern: Parser[String] = nonComma
  def artifactPattern: Parser[String] = nonComma
  private def afterPattern: Parser[AfterPattern] = {
    def onlyOptions = options map (AfterPattern(None, _))
    def both = artifactPattern ~ (separator ~> options).? map {
      case ap ~ opts => AfterPattern(Some(ap), opts getOrElse 0)
    }
    onlyOptions | both
  }

  def customResolver: Parser[xsbti.Repository] =
    name ~ ": " ~ basicUri ~ (separator ~> ivyPattern).? ~ (separator ~> afterPattern).? map {
      case name ~ ": " ~ uri ~ None ~ _ =>
        new MavenRepository(name, uri.toURL)
      case name ~ ": " ~ uri ~ Some(ivy) ~ None =>
        new IvyRepository(name, uri.toURL, ivy, ivy, false, false, false, false)
      case name ~ ": " ~ uri ~ Some(ivy) ~ Some(AfterPattern(artifactPattern, Flags(dOpt, sc, bo, mc))) =>
        new IvyRepository(name, uri.toURL, ivy, artifactPattern getOrElse ivy, mc, sc, dOpt, bo)
    }

  def resolver: Parser[xsbti.Repository] =
    predefinedResolver | customResolver

  def getResolver[T](in: String)(parser: Parser[T]): Option[T] =
    Parser.parse(in.trim, parser).right.toOption

  def apply(lines: Iterator[String]): Seq[xsbti.Repository] =
    if (lines.isEmpty) Nil
    else {
      if (lines.next != "[repositories]") throw new Exception("Repositories file must start with '[repositories]'")
      lines.flatMap(getResolver(_)(resolver)).toList
    }
  def apply(str: String): Seq[xsbti.Repository] = apply(str.lines)
  def apply(file: File): Seq[xsbti.Repository] = {
    if (!file.exists) Nil
    else apply(Source.fromFile(file).getLines)
  }

  object Flags {
    val descriptorOptionalFlag = 1 << 0
    val skipConsistencyCheckFlag = 1 << 1
    val bootOnlyFlag = 1 << 2
    val mavenCompatibleFlag = 1 << 3

    def unapply(flags: Int): Some[(Boolean, Boolean, Boolean, Boolean)] = {
      val dOpt = (flags & descriptorOptionalFlag) != 0
      val sc = (flags & skipConsistencyCheckFlag) != 0
      val bo = (flags & bootOnlyFlag) != 0
      val mc = (flags & mavenCompatibleFlag) != 0
      Some((dOpt, sc, bo, mc))
    }
  }
}