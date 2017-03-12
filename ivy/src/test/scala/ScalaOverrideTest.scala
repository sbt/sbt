package sbt

import java.io.File
import org.apache.ivy.core.module.id.{ ModuleId, ModuleRevisionId }
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor
import org.specs2._
import mutable.Specification

import cross.CrossVersionUtil
import IvyScala.OverrideScalaMediator
import ScalaArtifacts._

object ScalaOverrideTest extends Specification {
  val OtherOrgID = "other.org"

  def check(org0: String, version0: String)(org1: String, name1: String, version1: String) = {
    val scalaConfigs = Configurations.default.toVector filter { Configurations.underScalaVersion } map {
      _.name
    }
    val osm = new OverrideScalaMediator(org0, version0, scalaConfigs)

    val mrid = ModuleRevisionId.newInstance(org1, name1, version1)
    val dd = new DefaultDependencyDescriptor(mrid, false)
    dd.addDependencyConfiguration("compile", "compile")

    val res = osm.mediate(dd)
    res.getDependencyRevisionId must_== ModuleRevisionId.newInstance(org0, name1, version0)
  }

  "OverrideScalaMediator" should {
    "Override compiler version" in {
      check(Organization, "2.11.8")(Organization, CompilerID, "2.11.9")
    }
    "Override library version" in {
      check(Organization, "2.11.8")(Organization, LibraryID, "2.11.8")
    }
    "Override reflect version" in {
      check(Organization, "2.11.8")(Organization, ReflectID, "2.11.7")
    }
    "Override actors version" in {
      check(Organization, "2.11.8")(Organization, ActorsID, "2.11.6")
    }
    "Override scalap version" in {
      check(Organization, "2.11.8")(Organization, ScalapID, "2.11.5")
    }

    "Override default compiler organization" in {
      check(OtherOrgID, "2.11.8")(Organization, CompilerID, "2.11.9")
    }
    "Override default library organization" in {
      check(OtherOrgID, "2.11.8")(Organization, LibraryID, "2.11.8")
    }
    "Override default reflect organization" in {
      check(OtherOrgID, "2.11.8")(Organization, ReflectID, "2.11.7")
    }
    "Override default actors organization" in {
      check(OtherOrgID, "2.11.8")(Organization, ActorsID, "2.11.6")
    }
    "Override default scalap organization" in {
      check(OtherOrgID, "2.11.8")(Organization, ScalapID, "2.11.5")
    }

    "Override custom compiler organization" in {
      check(Organization, "2.11.8")(OtherOrgID, CompilerID, "2.11.9")
    }
    "Override custom library organization" in {
      check(Organization, "2.11.8")(OtherOrgID, LibraryID, "2.11.8")
    }
    "Override custom reflect organization" in {
      check(Organization, "2.11.8")(OtherOrgID, ReflectID, "2.11.7")
    }
    "Override custom actors organization" in {
      check(Organization, "2.11.8")(OtherOrgID, ActorsID, "2.11.6")
    }
    "Override custom scalap organization" in {
      check(Organization, "2.11.8")(OtherOrgID, ScalapID, "2.11.5")
    }
  }
}
