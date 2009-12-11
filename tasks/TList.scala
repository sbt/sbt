package xsbt

import Task.{bindTask, mapTask}

sealed trait TList
{
	type Head
	type Tail <: TList
	type HListType <: HList
	private[xsbt] def tasks: List[Task[_]]
	private[xsbt] def get(results: Results): HListType
}
sealed class TNil extends TList
{
	type Head = Nothing
	type Tail = TNil
	type HListType = HNil
	def ::[A](t: Task[A]) = TCons[A,HNil,TNil](t, this)
	private[xsbt] def tasks = Nil
	private[xsbt] def get(results: Results) = HNil
}
final case class TCons[H, HL <: HList, T <: TList { type HListType = HL}](head: Task[H], tail: T) extends TList
{
	type Head = H
	type Tail = T
	type HListType = HCons[H,HL]
	type This = TCons[H, HL, T]
	def ::[A](t: Task[A]) = TCons[A,HListType,This](t, this)
	private[xsbt] def tasks = head :: tail.tasks
	private[xsbt] def get(results: Results) = HCons(results(head), tail.get(results))
	private def getF = get _

	def map[X](f: HListType => X): Task[X] = mapTask(tasks: _*)(f compose getF)
	def bind[X](f: HListType => Result[X]): Task[X] = bindTask(tasks: _*)(f compose getF)
	def join: Task[HListType] = map(identity[HListType])
}
object TNil extends TNil