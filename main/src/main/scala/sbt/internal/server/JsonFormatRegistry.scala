/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package sbt
package internal
package server

import java.io.File
import java.net.{ URI, URL }
import scala.{ collection => sc }, sc.{ immutable => sci }, sci.{ Seq => sciSeq }
import scala.util.{ Left, Right }
import sbt.librarymanagement.LibraryManagementCodec._
import sjsonnew._

/** A registry of JsonFormat instances.
  *
  * Used to lookup, given a value of type 'A', its 'JsonFormat[A]' instance.
  */
object JsonFormatRegistry {
  type JF[A]   = JsonFormat[A]
  type CTag[A] = scala.reflect.ClassTag[A]

  @inline def ?[A](implicit z: A): A     = z
  @inline def classTag[A: CTag]: CTag[A] = ?

  val    UnitJF: JF[Unit]    = ?
  val BooleanJF: JF[Boolean] = ?
  val    ByteJF: JF[Byte]    = ?
  val   ShortJF: JF[Short]   = ?
  val    CharJF: JF[Char]    = ?
  val     IntJF: JF[Int]     = ?
  val    LongJF: JF[Int]     = ?
  val   FloatJF: JF[Float]   = ?
  val  DoubleJF: JF[Double]  = ?
  val  StringJF: JF[String]  = ?
  val  SymbolJF: JF[Symbol]  = ?
  val    FileJF: JF[File]    = ?
  val     URIJF: JF[URI]     = ?
  val     URLJF: JF[URL]     = ?

  def cast[A](z: JsonFormat[_]): JsonFormat[A]                = z.asInstanceOf[JsonFormat[A]]
  def castAndWrap[A](z: JsonFormat[_]): Option[JsonFormat[A]] = Some(cast(z))

  def optionJF[A](x: Option[A]): Option[JF[Option[A]]] = x match {
    case None    => castAndWrap(?[JF[Option[Int]]])
    case Some(x) => lookup(x) map (implicit elemJF => ?)
  }

  def eitherJF[A, B](x: Either[A, B]): Option[JF[Either[A, B]]] = x match {
    case Left(x) => lookup(x) map { implicit lJF: JF[A] =>
      implicit val rJF: JF[B] = cast(UnitJF)
      ?[JF[Either[A, B]]]
    }
    case Right(x) => lookup(x) map { implicit rJF: JF[B] =>
      implicit val lJF: JF[A] = cast(UnitJF)
      ?[JF[Either[A, B]]]
    }
  }

  def tuple1JF[A](x: Tuple1[A]): Option[JF[Tuple1[A]]] = lookup(x._1) map { implicit elemJF: JF[A] => ? }

  def tuple2JF[A, B](x: (A, B)): Option[JF[(A, B)]] =
    for (aJF <- lookup(x._1); bJF <- lookup(x._2)) yield {
      implicit val aJFi: JF[A] = aJF
      implicit val bJFi: JF[B] = bJF
      ?
    }

  def tuple3JF[A, B, C](x: (A, B, C)): Option[JF[(A, B, C)]] =
    for (aJF <- lookup(x._1); bJF <- lookup(x._2); cJF <- lookup(x._3)) yield {
      implicit val aJFi: JF[A] = aJF
      implicit val bJFi: JF[B] = bJF
      implicit val cJFi: JF[C] = cJF
      ?
    }

  def tuple4JF[A, B, C, D](x: (A, B, C, D)): Option[JF[(A, B, C, D)]] =
    for (aJF <- lookup(x._1); bJF <- lookup(x._2); cJF <- lookup(x._3); dJF <- lookup(x._4)) yield {
      implicit val aJFi: JF[A] = aJF
      implicit val bJFi: JF[B] = bJF
      implicit val cJFi: JF[C] = cJF
      implicit val dJFi: JF[D] = dJF
      ?
    }

  def listJF[A](x: List[A]): Option[JF[List[A]]] = x match {
    case Nil    => castAndWrap(?[JF[List[Int]]])
    case x :: _ => lookup(x) map { implicit elemJF: JF[A] => ? }
  }

  def arrayJF[A](x: Array[A]): Option[JF[Array[A]]] = x match {
    case Array()      => castAndWrap(?[JF[Array[Int]]])
    case Array(x, _*) => lookup(x) map { implicit elemJF: JF[A] =>
      implicit val ctag: CTag[A] = classTag[AnyRef].asInstanceOf[CTag[A]] // if A is a primitive.. boom
      ?
    }
  }

  // Map is a PITA, because it needs a JsonKeyFormat, not the "Key"
//  def mapJF[A, B](x: Map[A, B]): Option[JF[Map[A, B]]] = x.headOption match {
//    case None         => castAndWrap(?[JF[Map[Int, Int]]])
//    case Some((a, b)) => for (aJF <- lookup(a); bJF <- lookup(b)) yield {
//      implicit val aJFi: JF[A] = aJF
//      implicit val bJFi: JF[B] = bJF
//      ?[JF[Map[A, B]]]
//      mapFormat[A, B]
//    }
//  }

  def vectorJF[A](x: Vector[A]): Option[JF[Vector[A]]] = x match {
    case Vector()      => castAndWrap(?[JF[Vector[Int]]])
    case Vector(x, _*) => lookup(x) map { implicit elemJF: JF[A] => ? }
  }

  def sciSeqJF[A](x: sciSeq[A]): Option[JF[sciSeq[A]]] = x match {
    case sci.Seq()      => castAndWrap(?[JF[sciSeq[Int]]])
    case sci.Seq(x, _*) => lookup(x) map { implicit elemJF: JF[A] => ? }
  }

  def seqJF[A](x: Seq[A]): Option[JF[Seq[A]]] = x match {
    case Seq()      => castAndWrap(?[JF[Seq[Int]]])
    case Seq(x, _*) => lookup(x) map { implicit elemJF: JF[A] => ? }
  }

  // TODO: Upgrade sjsonnew version
//  def optional[A](x: Optional[A]): Option[JF[Optional[A]]] =
//    if (x.isPresent) lookup(x.get) map { implicit elemJF: JF[A] => ? }
//    else castAndWrap(?[JF[Optional[Int]]])

  def lookup[A](value: A): Option[JsonFormat[A]] = value match {
    case _: Unit         => castAndWrap[A](   UnitJF)
    case _: Boolean      => castAndWrap[A](BooleanJF)
    case _: Byte         => castAndWrap[A](   ByteJF)
    case _: Short        => castAndWrap[A](  ShortJF)
    case _: Char         => castAndWrap[A](   CharJF)
    case _: Int          => castAndWrap[A](    IntJF)
    case _: Long         => castAndWrap[A](   LongJF)
    case _: Float        => castAndWrap[A](  FloatJF)
    case _: Double       => castAndWrap[A]( DoubleJF)
    case _: String       => castAndWrap[A]( StringJF)
    case _: Symbol       => castAndWrap[A]( SymbolJF)
    case _: File         => castAndWrap[A](   FileJF)
    case _: URI          => castAndWrap[A](    URIJF)
    case _: URL          => castAndWrap[A](    URLJF)
    case x: Option[_]    =>   optionJF(x) map cast
    case x: Either[_, _] =>   eitherJF(x) map cast
    case x: Tuple1[_]    =>   tuple1JF(x) map cast
    case x: (_, _)       =>   tuple2JF(x) map cast
    case x: (_, _, _)    =>   tuple3JF(x) map cast
    case x: (_, _, _, _) =>   tuple4JF(x) map cast
    case x: List[_]      =>     listJF(x) map cast
    case x: Array[_]     =>    arrayJF(x) map cast
//  case x: Map[_, _]    =>      mapJF(x) map cast
    case x: Vector[_]    =>   vectorJF(x) map cast
    case x: sciSeq[_]    =>   sciSeqJF(x) map cast
    case x: Seq[_]       =>      seqJF(x) map cast
//  case x: Optional[_]  => optionalJF(x) map cast
    case _               => None
  }
}
