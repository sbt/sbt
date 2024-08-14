package sbt.util

import sbt.io.IO
import sbt.io.syntax.*

object DigestTest extends verify.BasicTestSuite:
  test("parse murmur3") {
    val d = Digest("murmur3-00000000000000000000000000000000/0")
    val dummy = Digest.dummy(0L)
    assert(d == dummy)
  }

  test("parse md5") {
    val expected = Digest("md5-d41d8cd98f00b204e9800998ecf8427e/0")
    testEmptyFile("md5", expected)
  }

  test("parse sha1") {
    val expected = Digest("sha1-da39a3ee5e6b4b0d3255bfef95601890afd80709/0")
    testEmptyFile("sha1", expected)
  }

  test("sha256") {
    val hashOfNull = Digest.sha256Hash(Array[Byte]())
    val expected =
      Digest("sha256-e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855/0")
    assert(hashOfNull == expected)
    testEmptyFile("sha256", expected)
  }

  test("parse sha384") {
    val expected = Digest(
      "sha384-38b060a751ac96384cd9327eb1b1e36a21fdb71114be07434c0cc7bf63f6e1da274edebfe76f65fbd51ad2f14898b95b/0"
    )
    testEmptyFile("sha384", expected)
  }

  test("sha512") {
    val expected = Digest(
      "sha512-cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e/0"
    )
    testEmptyFile("sha512", expected)
  }

  test("digest composition") {
    val dummy1 = Digest.dummy(0L)
    val dummy2 = Digest.dummy(0L)
    val expected =
      Digest("sha256-66687aadf862bd776c8fc18b8e9f8e20089714856ee233b3902a591d0d5f2925/32")
    assert(Digest.sha256Hash(dummy1, dummy2) == expected)
  }

  def testEmptyFile(algo: String, expected: Digest): Unit =
    IO.withTemporaryDirectory: tempDir =>
      val empty = tempDir / "empty.txt"
      IO.touch(empty)
      val d_sha1 = Digest(algo, empty.toPath())
      assert(d_sha1 == expected)

end DigestTest
