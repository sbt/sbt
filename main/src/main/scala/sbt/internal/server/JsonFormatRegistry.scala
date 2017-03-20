/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package sbt
package internal
package server

import java.io.File
import java.net.{ URI, URL }
import scala.{ collection => sc }, sc.{ immutable => sci }, sci.{ Seq => sciSeq }
import sbt.librarymanagement.LibraryManagementCodec._
import sjsonnew._

/** A registry of JsonFormat instances.
  *
  * Used to lookup, given a value of type 'A', its 'JsonFormat[A]' instance.
  */
object JsonFormatRegistry {
  type MF[A]   = Manifest[A]
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

  // TODO: Any way to de-duplify here?
  def   tuple1JF[A: Manifest]: Option[JF[  Tuple1[A]]] = lookup(manifest[A]) map { implicit elemJF: JF[A] => ? }
  def   optionJF[A: Manifest]: Option[JF[  Option[A]]] = lookup(manifest[A]) map { implicit elemJF: JF[A] => ? }
  def     listJF[A: Manifest]: Option[JF[    List[A]]] = lookup(manifest[A]) map { implicit elemJF: JF[A] => ? }
  def    arrayJF[A: Manifest]: Option[JF[   Array[A]]] = lookup(manifest[A]) map { implicit elemJF: JF[A] => ? }
  def   vectorJF[A: Manifest]: Option[JF[  Vector[A]]] = lookup(manifest[A]) map { implicit elemJF: JF[A] => ? }
  def   sciSeqJF[A: Manifest]: Option[JF[  sciSeq[A]]] = lookup(manifest[A]) map { implicit elemJF: JF[A] => ? }
  def      seqJF[A: Manifest]: Option[JF[     Seq[A]]] = lookup(manifest[A]) map { implicit elemJF: JF[A] => ? }
//def optionalJF[A: Manifest]: Option[JF[Optional[A]]] = lookup(manifest[A]) map { implicit elemJF: JF[A] => ? } // TODO: Upgrade sjsonnew version

  def eitherJF[A: Manifest, B: Manifest]: Option[JF[Either[A, B]]] =
    for (aJF <- lookup(manifest[A]); bJF <- lookup(manifest[B])) yield {
      implicit val aJFi: JF[A] = aJF
      implicit val bJFi: JF[B] = bJF
      ?
    }


  def tuple2JF[A: Manifest, B: Manifest]: Option[JF[(A, B)]] =
    for (aJF <- lookup(manifest[A]); bJF <- lookup(manifest[B])) yield {
      implicit val aJFi: JF[A] = aJF
      implicit val bJFi: JF[B] = bJF
      ?
    }

  def tuple3JF[A: Manifest, B: Manifest, C: Manifest]: Option[JF[(A, B, C)]] =
    for (aJF <- lookup(manifest[A]); bJF <- lookup(manifest[B]); cJF <- lookup(manifest[C])) yield {
      implicit val aJFi: JF[A] = aJF
      implicit val bJFi: JF[B] = bJF
      implicit val cJFi: JF[C] = cJF
      ?
    }

  def tuple4JF[A: Manifest, B: Manifest, C: Manifest, D: Manifest]: Option[JF[(A, B, C, D)]] =
    for (aJF <- lookup(manifest[A]); bJF <- lookup(manifest[B]); cJF <- lookup(manifest[C]); dJF <- lookup(manifest[D])) yield {
      implicit val aJFi: JF[A] = aJF
      implicit val bJFi: JF[B] = bJF
      implicit val cJFi: JF[C] = cJF
      implicit val dJFi: JF[D] = dJF
      ?
    }

  // Map is a PITA, because it needs a JsonKeyFormat, note the "Key"
//  def mapJF[A: Manifest, B: Manifest]: Option[JF[Map[A, B]]] =
//    for (aJF <- lookup(manifest[A]); bJF <- lookup(manifest[A])) yield {
//      implicit val aJFi: JF[A] = aJF
//      implicit val bJFi: JF[B] = bJF
//      ?
//    }
//  }

  def lookup[A: Manifest]: Option[JsonFormat[A]] = manifest[A] match {
    case Manifest.Unit                                => castAndWrap[A](   UnitJF)
    case Manifest.Boolean                             => castAndWrap[A](BooleanJF)
    case Manifest.Byte                                => castAndWrap[A](   ByteJF)
    case Manifest.Short                               => castAndWrap[A](  ShortJF)
    case Manifest.Char                                => castAndWrap[A](   CharJF)
    case Manifest.Int                                 => castAndWrap[A](    IntJF)
    case Manifest.Long                                => castAndWrap[A](   LongJF)
    case Manifest.Float                               => castAndWrap[A](  FloatJF)
    case Manifest.Double                              => castAndWrap[A]( DoubleJF)
    case m if m.runtimeClass == classOf[String]       => castAndWrap[A]( StringJF)
    case m if m.runtimeClass == classOf[Symbol]       => castAndWrap[A]( SymbolJF)
    case m if m.runtimeClass == classOf[File]         => castAndWrap[A](   FileJF)
    case m if m.runtimeClass == classOf[URI]          => castAndWrap[A](    URIJF)
    case m if m.runtimeClass == classOf[URL]          => castAndWrap[A](    URLJF)
    case m if m.runtimeClass == classOf[Option[_]]    =>   optionJF(m.typeArguments.head) map cast
    case m if m.runtimeClass == classOf[Either[_, _]] =>   eitherJF(m.typeArguments.head, m.typeArguments(1)) map cast
    case m if m.runtimeClass == classOf[Tuple1[_]]    =>   tuple1JF(m.typeArguments.head) map cast
    case m if m.runtimeClass == classOf[(_, _)]       =>   tuple2JF(m.typeArguments.head, m.typeArguments(1)) map cast
    case m if m.runtimeClass == classOf[(_, _, _)]    =>   tuple3JF(m.typeArguments.head, m.typeArguments(1), m.typeArguments(2)) map cast
    case m if m.runtimeClass == classOf[(_, _, _, _)] =>   tuple4JF(m.typeArguments.head, m.typeArguments(1), m.typeArguments(2), m.typeArguments(3)) map cast
    case m if m.runtimeClass == classOf[List[_]]      =>     listJF(m.typeArguments.head) map cast
    case m if m.runtimeClass == classOf[Array[_]]     =>    arrayJF(m.typeArguments.head) map cast
//  case m if m.runtimeClass == classOf[Map[_, _]]    =>      mapJF(m.typeArguments.head, m.typeArguments(1)) map cast
    case m if m.runtimeClass == classOf[Vector[_]]    =>   vectorJF(m.typeArguments.head) map cast
    case m if m.runtimeClass == classOf[sciSeq[_]]    =>   sciSeqJF(m.typeArguments.head) map cast
    case m if m.runtimeClass == classOf[Seq[_]]       =>      seqJF(m.typeArguments.head) map cast
//  case m if m.runtimeClass == classOf[Optional[_]]  => optionalJF(m.typeArguments.head) map cast
    case _                                            => None
  }
}
