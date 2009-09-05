package xsbt

import java.net.URL
import java.util.Enumeration

final class NullLoader extends ClassLoader
{
	override final def loadClass(className: String, resolve: Boolean): Class[_] = throw new ClassNotFoundException("No classes can be loaded from the null loader")
	override def getResource(name: String): URL = null
	override def getResources(name: String): Enumeration[URL] = null
}

class DifferentLoaders(message: String, val loaderA: ClassLoader, val loaderB: ClassLoader) extends ClassNotFoundException(message)
class DualLoader(parentA: ClassLoader, aOnlyClasses: String => Boolean, aOnlyResources: String => Boolean,
	parentB: ClassLoader, bOnlyClasses: String => Boolean, bOnlyResources: String => Boolean) extends ClassLoader(new NullLoader)
{
	def this(parentA: ClassLoader, aOnly: String => Boolean, parentB: ClassLoader, bOnly: String => Boolean) =
		this(parentA, aOnly, aOnly, parentB, bOnly, bOnly)
	override final def loadClass(className: String, resolve: Boolean): Class[_] =
	{
		val c =
			if(aOnlyClasses(className))
				parentA.loadClass(className)
			else if(bOnlyClasses(className))
				parentB.loadClass(className)
			else
			{
				val classA = parentA.loadClass(className)
				val classB = parentB.loadClass(className)
				if(classA.getClassLoader eq classB.getClassLoader)
					classA
				else
					throw new DifferentLoaders("Parent class loaders returned different classes for '" + className + "'", classA.getClassLoader, classB.getClassLoader)
			}
		if(resolve)
			resolveClass(c)
		c
	}
	override def getResource(name: String): URL =
	{
		if(aOnlyResources(name))
			parentA.getResource(name)
		else if(bOnlyResources(name))
			parentB.getResource(name)
		else
		{
			val urlA = parentA.getResource(name)
			val urlB = parentB.getResource(name)
			if(urlA eq null)
				urlB
			else
				urlA
		}
	}
	override def getResources(name: String): Enumeration[URL] =
	{
		if(aOnlyResources(name))
			parentA.getResources(name)
		else if(bOnlyResources(name))
			parentB.getResources(name)
		else
		{
			val urlsA = parentA.getResources(name)
			val urlsB = parentB.getResources(name)
			if(urlsA eq null)
				urlsB
			else if(urlsB eq null)
				urlsA
			else
				new DualEnumeration(urlsA, urlsB)
		}
	}
}

final class DualEnumeration[T](a: Enumeration[T], b: Enumeration[T]) extends Enumeration[T]
{
	// invariant: current.hasMoreElements or current eq b
	private[this] var current = if(a.hasMoreElements) a else b
	def hasMoreElements = current.hasMoreElements
	def nextElement =
	{
		val element = current.nextElement
		if(!current.hasMoreElements)
			current = b
		element
	}
}