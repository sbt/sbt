package xsbt.boot	

	import org.scalacheck._
	import Prop._

object VersionParts extends Properties("VersionParts")
{
	property("Valid version, no qualifier") = Prop.forAll { (x0: Int, y0: Int, z0: Int) =>
		val (x,y,z) = (norm(x0), norm(y0), norm(z0))
		val str = s"$x.$y.$z"
		val expected =
			s"$x.$y.$z" ::
			s"$x.$y" ::
			"" ::
			Nil
		check(str, expected)
	}

	property("Valid version with qualifier") = Prop.forAll { (x0: Int, y0: Int, z0: Int, q0: String) =>
		val (x,y,z,q) = (norm(x0), norm(y0), norm(z0), normS(q0))
		val str = s"$x.$y.$z-$q"
		val expected =
			s"$x.$y.$z-$q" ::
			s"$x.$y.$z" ::
			s"$x.$y" ::
			"" ::
			Nil
		check(str, expected)
	}

	property("Invalid version") = Prop.forAll { (x0: Int, y0: Int, z0: Int, q0: String) =>
		val (x,y,z,q) = (norm(x0), norm(y0), norm(z0), normS(q0))
		val strings =
			x.toString ::
			s"$x.$y" ::
			s"$x.$y-$q" ::
			s"$x.$y.$z.$q" ::
			Nil
		all(strings.map(str => check(str, Configuration.noMatchParts)) : _*)
	}

	private[this] def check(versionString: String, expectedParts: List[String]) = 
	{
		def printParts(s: List[String]): String = s.map("'" + _ + "'").mkString("(", ", ", ")")
		val actual = Configuration.versionParts(versionString)
		s"Version string '$versionString'" |:
		s"Expected '${printParts(expectedParts)}'" |:
		s"Actual'${printParts(actual)}'" |:
		(actual == expectedParts)
	}

	// Make `i` non-negative
	private[this] def norm(i: Int): Int =
		if(i == Int.MinValue) Int.MaxValue else math.abs(i)

	// Make `s` non-empty and suitable for java.util.regex input
	private[this] def normS(s: String): String =
	{
		val filtered = s filter validChar
		if(s.isEmpty) "q" else s
	}

	// strip whitespace and characters not supported by Pattern
	private[this] def validChar(c: Char) =
		!java.lang.Character.isWhitespace(c) &&
		!java.lang.Character.isISOControl(c) &&
		!Character.isHighSurrogate(c) &&
		!Character.isLowSurrogate(c)
}