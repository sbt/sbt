package sbt

sealed trait Settings
{
	def get[T](key: AttributeKey[T], path: List[String]): Option[T]
	def set[T](key: AttributeKey[T], path: List[String], value: T): Settings
}
object Settings
{
	def empty: Settings = new Basic(Map.empty)
	def x = 3

	private[this] class Basic(val roots: Map[ List[String], AttributeMap ]) extends Settings
	{
		def get[T](key: AttributeKey[T], path: List[String]): Option[T] =
		{
			def notFound = path match {
				case Nil => None
				case x :: xs => get(key, xs)
			}
			(roots get path) flatMap ( _ get key ) orElse notFound
		}
		def set[T](key: AttributeKey[T], path: List[String], value: T): Settings =
		{
			val amap = (roots get path) getOrElse AttributeMap.empty
			new Basic( roots updated(path, amap put(key, value)) )
		}
	}
}