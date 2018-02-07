package coursier.cli

import java.io.{File, FileWriter}

import coursier.cli.options.CommonOptions
import org.junit.runner.RunWith
import org.scalatest.FlatSpec
import org.scalatest.junit.JUnitRunner


@RunWith(classOf[JUnitRunner])
class CliUnitTest extends FlatSpec {

  def withFile(content: String)(testCode: (File, FileWriter) => Any) {
    val file = File.createTempFile("hello", "world") // create the fixture
    val writer = new FileWriter(file)
    writer.write(content)
    writer.flush()
    try {
      testCode(file, writer) // "loan" the fixture to the test
    }
    finally {
      writer.close()
      file.delete()
    }
  }

  "Normal text" should "parse correctly" in withFile(
    "org1:name1--org2:name2") { (file, writer) =>
    val opt = CommonOptions(localExcludeFile = file.getAbsolutePath)
    val helper = new Helper(opt, Seq())
    assert(helper.localExcludeMap.equals(Map("org1:name1" -> Set(("org2", "name2")))))
  }

  "Multiple excludes" should "be combined" in withFile(
    "org1:name1--org2:name2\n" +
      "org1:name1--org3:name3\n" +
      "org4:name4--org5:name5") { (file, writer) =>

    val opt = CommonOptions(localExcludeFile = file.getAbsolutePath)
    val helper = new Helper(opt, Seq())
    assert(helper.localExcludeMap.equals(Map(
      "org1:name1" -> Set(("org2", "name2"), ("org3", "name3")),
      "org4:name4" -> Set(("org5", "name5")))))
  }

  "extra --" should "error" in withFile(
    "org1:name1--org2:name2--xxx\n" +
      "org1:name1--org3:name3\n" +
      "org4:name4--org5:name5") { (file, writer) =>
    assertThrows[SoftExcludeParsingException]({
      val opt = CommonOptions(localExcludeFile = file.getAbsolutePath)
      new Helper(opt, Seq())
    })
  }

  "child has no name" should "error" in withFile(
    "org1:name1--org2:") { (file, writer) =>
    assertThrows[SoftExcludeParsingException]({
      val opt = CommonOptions(localExcludeFile = file.getAbsolutePath)
      new Helper(opt, Seq())
    })
  }

  "child has nothing" should "error" in withFile(
    "org1:name1--:") { (file, writer) =>
    assertThrows[SoftExcludeParsingException]({
      val opt = CommonOptions(localExcludeFile = file.getAbsolutePath)
      new Helper(opt, Seq())
    })
  }

}
