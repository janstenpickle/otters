package statestream.laws.discipline

import cats.instances.eq._
import cats.laws.discipline.SemigroupalTests.Isomorphisms
import cats.laws.discipline._
import cats.{ContravariantSemigroupal, Eq, Functor}
import org.scalacheck.Prop.forAll
import org.scalacheck.{Arbitrary, Cogen}
import statestream.Pipe
import statestream.laws.StreamLaws

trait StreamTests[F[_]] extends ApplicativeTests[F] {
  def laws: StreamLaws[F]

  def stream[A: Arbitrary, B: Arbitrary, C: Arbitrary](
    implicit
    ArbFA: Arbitrary[F[A]],
    ArbFB: Arbitrary[F[B]],
    ArbFC: Arbitrary[F[C]],
    ArbFAtoB: Arbitrary[F[A => B]],
    ArbFBtoC: Arbitrary[F[B => C]],
    ArbPipeAToB: Arbitrary[Pipe[F, A, B]],
    ArbPipeBToC: Arbitrary[Pipe[F, B, C]],
    CogenA: Cogen[A],
    CogenB: Cogen[B],
    CogenC: Cogen[C],
    EqFA: Eq[F[A]],
    EqFB: Eq[F[B]],
    EqFC: Eq[F[C]],
    EqFABC: Eq[F[(A, B, C)]],
    EqFSeqA: Eq[F[Seq[A]]],
    iso: Isomorphisms[F]
  ): RuleSet = {
    implicit def functorF: Functor[F] = laws.F
    implicit val EqFAB: Eq[F[(A, B)]] =
      ContravariantSemigroupal[Eq].composeFunctor[F].product(EqFA, EqFB)

    new DefaultRuleSet(
      name = "stream",
      parent = Some(applicative[A, B, C]),
      "flatMap associativity" -> forAll(laws.flatMapAssociativity[A, B, C] _),
      "flatMap consistent apply" -> forAll(laws.flatMapConsistentApply[A, B] _),
      "mapConcat associativity" -> forAll(laws.mapConcatAssociativity[A, B, C] _),
      "pipe covariant composition" -> forAll(laws.pipeCovariantComposition[A, B, C] _),
      "zip homomorphism" -> forAll(laws.zipHomomorphism[A, B] _),
      "grouped homomorphism" -> forAll(laws.groupedHomomorphism[A] _),
      "grouped within homomorphism" -> forAll(laws.groupedWithinHomomorphism[A] _)
    )
  }
}

object StreamTests {
  def apply[F[_]](implicit ev: StreamLaws[F]): StreamTests[F] = new StreamTests[F] {
    override def laws: StreamLaws[F] = ev
  }
}
