package sbt
package logic

object Test {
	val A = Atom("A")
	val B = Atom("B")
	val C = Atom("C")
	val D = Atom("D")
	val E = Atom("E")
	val F = Atom("F")
	val G = Atom("G")

	val clauses =
		A.proves(B) ::
		A.proves(F) ::
		B.proves(F) ::
		F.proves(A) ::
		(!C).proves(F) ::
		D.proves(C) ::
		C.proves(D) ::
		Nil

	val cycles = Logic.reduceAll(clauses, Set())

	val badClauses =
		A.proves(D) ::
		clauses

	val excludedNeg = {
		val cs =
			(!A).proves(B) ::
			Nil
		val init =
			(!A) ::
			(!B) ::
			Nil
		Logic.reduceAll(cs, init.toSet)
	}

	val excludedPos = {
		val cs =
			A.proves(B) ::
			Nil
		val init =
			A ::
			(!B) ::
			Nil
		Logic.reduceAll(cs, init.toSet)
	}

	val trivial = {
		val cs =
			Formula.True.proves(A) ::
			Nil
		Logic.reduceAll(cs, Set.empty)
	}

	val lessTrivial = {
		val cs =
			Formula.True.proves(A) ::
			Formula.True.proves(B) ::
			(A && B && (!C)).proves(D) ::
			Nil
		Logic.reduceAll(cs, Set())
	}

	val ordering = {
		val cs =
			E.proves(F) ::
			(C && !D).proves(E) ::
			(A && B).proves(C) ::
			Nil
		Logic.reduceAll(cs, Set(A,B))
	}

	def all {
		println(s"Cycles: $cycles")
		println(s"xNeg: $excludedNeg")
		println(s"xPos: $excludedPos")
		println(s"trivial: $trivial")
		println(s"lessTrivial: $lessTrivial")
		println(s"ordering: $ordering")
	}
}
