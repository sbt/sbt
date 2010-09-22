/* sbt -- Simple Build Tool
 * Copyright 2008 Mark Harrah
 */
package sbt

import org.scalacheck._

object VersionSpecification extends Properties("Version")
{
	import ArbitraryVersion._
	specify("Empty or whitespace only string not allowed, all others allowed",
		(s: String) => Version.fromString(s).isLeft == s.trim.isEmpty)
	specify("BasicVersion round trips", checkRoundTrip _)
	specify("BasicVersion increment major", checkIncrementMajor _)
	specify("BasicVersion increment minor", checkIncrementMinor _)
	specify("BasicVersion increment micro", checkIncrementMicro _)
	
	private def checkRoundTrip(v: BasicVersion) =
	{
		val v2 = Version.fromString(v.toString)
		v2.isRight && v2.right.get == v
	}
	private def checkIncrementMinor(v: BasicVersion) = checkIncrement(v, _.incrementMinor)
	private def checkIncrementMajor(v: BasicVersion) = checkIncrement(v, _.incrementMajor)
	private def checkIncrementMicro(v: BasicVersion) = checkIncrement(v, _.incrementMicro)
	private def checkIncrement(v: BasicVersion, increment: (BasicVersion => BasicVersion)) =
	{
		val vNew = increment(v)
		checkRoundTrip(vNew)  && vNew != v
	}
}
object ArbitraryVersion
{
	implicit lazy val arbBasicVersion: Arbitrary[BasicVersion] = Arbitrary(genBasicVersion)
	implicit lazy val arbOpaqueVersion: Arbitrary[OpaqueVersion] = Arbitrary(genOpaqueVersion)
	implicit lazy val arbVersion: Arbitrary[Version] = Arbitrary(genVersion)
	
	import Arbitrary._
	import Math.abs
	lazy val genBasicVersion =
		for{major <- arbInt.arbitrary
			minor <- arbOption[Int].arbitrary
			micro <- arbOption[Int].arbitrary
			extra <- genExtra }
		yield
		{
			 if(minor.isEmpty && micro.isDefined)
			 	BasicVersion(abs(major), micro.map(abs), None, extra)
			 else
			 	BasicVersion(abs(major), minor.map(abs), micro.map(abs), extra)
		}
	lazy val genOpaqueVersion = for(versionString <- arbString.arbitrary if !versionString.trim.isEmpty) yield OpaqueVersion(versionString)
	lazy val genVersion = Gen.frequency((5,genBasicVersion), (1,genOpaqueVersion))
	
	private lazy val genExtra =
		for(extra <- arbOption[String].arbitrary;
			val trimmedExtra = extra.map(_.trim.filter(c => !java.lang.Character.isISOControl(c)).toString);
			if Version.isValidExtra(trimmedExtra))
		yield
			trimmedExtra
}