/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import java.lang.Double as JDouble
import java.nio.charset.StandardCharsets.UTF_8

import sjsonnew.{ BuilderFacade, SimpleBuilderFacade, SupportHasher }

/**
 * Hashes a HashWriter input into a full-width sha256 Digest. Leaves hash their bytes under a
 * per-kind tag; arrays and objects combine their child digests under their own tag, with
 * objects sorted by key so the result is order-independent. Unlike the 32-bit murmur hasher,
 * distinct inputs do not collide within a build-sized population.
 */
private[sbt] object DigestHasher extends SupportHasher[Digest]:
  implicit val facade: BuilderFacade[Digest] = FacadeImpl

  private val arrayTag: Digest = Digest.sha256Hash(Array[Byte](6))
  private val objectTag: Digest = Digest.sha256Hash(Array[Byte](7))

  private def tagged(tag: Byte, bytes: Array[Byte]): Digest =
    Digest.sha256Hash(Array(tag) ++ bytes)

  private def longToBytes(l: Long): Array[Byte] =
    val b = new Array[Byte](8)
    var x = l
    var i = 0
    while i < 8 do
      b(i) = (x & 0xff).toByte
      x >>>= 8
      i += 1
    b

  private object FacadeImpl extends SimpleBuilderFacade[Digest]:
    def jnull(): Digest = tagged(0, Array.emptyByteArray)
    def jfalse(): Digest = tagged(1, Array.emptyByteArray)
    def jtrue(): Digest = tagged(2, Array.emptyByteArray)
    def jint(i: Int): Digest = jlong(i.toLong)
    def jlong(l: Long): Digest = tagged(3, longToBytes(l))
    def jdouble(d: Double): Digest = tagged(4, longToBytes(JDouble.doubleToRawLongBits(d)))
    def jnumstring(s: String): Digest = jstring(s)
    def jintstring(s: String): Digest = jstring(s)
    def jbigdecimal(d: BigDecimal): Digest = jstring(d.toString)
    def jstring(s: String): Digest = tagged(5, s.getBytes(UTF_8))
    def jarray(vs: List[Digest]): Digest = Digest.sha256Hash((arrayTag +: vs)*)
    def jobject(vs: Map[String, Digest]): Digest =
      val sorted = vs.toSeq.sortBy(_._1).flatMap((k, v) => Seq(jstring(k), v))
      Digest.sha256Hash((objectTag +: sorted)*)
