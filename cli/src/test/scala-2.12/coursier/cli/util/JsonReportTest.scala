package coursier.cli.util

import coursier.cli.CliTestLib
import org.junit.runner.RunWith
import org.scalatest.FlatSpec
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class JsonReportTest extends FlatSpec with CliTestLib {
  "empty JsonReport" should "be empty" in {
    val report: String = JsonReport[String](IndexedSeq(), Map(), Set())(
      children = _ => Seq(),
      reconciledVersionStr = _ => "",
      requestedVersionStr = _ => "",
      getFile = _ => Option("")
    )

    assert(
      report == "{\"conflict_resolution\":{},\"dependencies\":[],\"version\":\"0.1.0\"}")
  }

  "JsonReport containing two deps" should "not be empty" in {
    val children = Map("a" -> Seq("b"), "b" -> Seq())
    val report: String = JsonReport[String](
      roots = IndexedSeq("a", "b"),
      conflictResolutionForRoots = Map(),
      overrideClassifiers = Set()
    )(
      children = children(_),
      reconciledVersionStr = s => s"$s:reconciled",
      requestedVersionStr = s => s"$s:requested",
      getFile = _ => Option("")
    )

    assert(
      report == "{\"conflict_resolution\":{},\"dependencies\":[" +
        "{\"coord\":\"a:reconciled\",\"file\":\"\",\"dependencies\":[\"b:reconciled\"]}," +
        "{\"coord\":\"b:reconciled\",\"file\":\"\",\"dependencies\":[]}]," +
        "\"version\":\"0.1.0\"}")
  }
  "JsonReport containing two deps" should "be sorted alphabetically regardless of input order" in {
    val children = Map("a" -> Seq("b"), "b" -> Seq())
    val report: String = JsonReport[String](
      roots = IndexedSeq( "b", "a"),
      conflictResolutionForRoots = Map(),
      overrideClassifiers = Set()
    )(
      children = children(_),
      reconciledVersionStr = s => s"$s:reconciled",
      requestedVersionStr = s => s"$s:requested",
      getFile = _ => Option("")
    )

    assert(
      report == "{\"conflict_resolution\":{},\"dependencies\":[" +
        "{\"coord\":\"a:reconciled\",\"file\":\"\",\"dependencies\":[\"b:reconciled\"]}," +
        "{\"coord\":\"b:reconciled\",\"file\":\"\",\"dependencies\":[]}]," +
        "\"version\":\"0.1.0\"}")
  }
}
