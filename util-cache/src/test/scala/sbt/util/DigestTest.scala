package sbt.util

object DigestTest extends verify.BasicTestSuite:
  test("murmur3") {
    val d = Digest("murmur3-00000000000000000000000000000000")
    val dummy = Digest.dummy(0L)
    assert(d == dummy)
  }

  test("md5") {
    val d = Digest("md5-d41d8cd98f00b204e9800998ecf8427e")
  }

  test("sha1") {
    val d = Digest("sha1-da39a3ee5e6b4b0d3255bfef95601890afd80709")
  }

  test("sha256") {
    val hashOfNull = Digest.sha256Hash(Array[Byte]())
    val d = Digest("sha256-e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
    assert(hashOfNull == d)
  }

  test("sha384") {
    val d = Digest(
      "sha384-38b060a751ac96384cd9327eb1b1e36a21fdb71114be07434c0cc7bf63f6e1da274edebfe76f65fbd51ad2f14898b95b"
    )
  }

  test("sha512") {
    val d = Digest(
      "sha512-cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e"
    )
  }

  test("digest composition") {
    val dummy1 = Digest.dummy(0L)
    val dummy2 = Digest.dummy(0L)
    val expected = Digest("sha256-66687aadf862bd776c8fc18b8e9f8e20089714856ee233b3902a591d0d5f2925")
    assert(Digest.sha256Hash(dummy1, dummy2) == expected)
  }
end DigestTest
