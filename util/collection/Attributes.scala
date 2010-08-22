/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

import Types._

// T must be invariant to work properly.
//  Because it is sealed and the only instances go through make,
//  a single AttributeKey instance cannot conform to AttributeKey[T] for different Ts
sealed trait AttributeKey[T]
object AttributeKey
{
	def make[T]: AttributeKey[T] = new AttributeKey[T] {}
}

trait AttributeMap
{
	def apply[T](k: AttributeKey[T]): T
	def get[T](k: AttributeKey[T]): Option[T]
	def contains[T](k: AttributeKey[T]): Boolean
	def put[T](k: AttributeKey[T], value: T): AttributeMap
}
object AttributeMap
{
	def empty: AttributeMap = new BasicAttributeMap(Map.empty)
}
private class BasicAttributeMap(backing: Map[AttributeKey[_], Any]) extends AttributeMap
{
	def apply[T](k: AttributeKey[T]) = backing(k).asInstanceOf[T]
	def get[T](k: AttributeKey[T]) = backing.get(k).asInstanceOf[Option[T]]
	def contains[T](k: AttributeKey[T]) = backing.contains(k)
	def put[T](k: AttributeKey[T], value: T): AttributeMap = new BasicAttributeMap( backing.updated(k, value) )
}