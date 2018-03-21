package otters.laws.discipline

import cats.Eq
import cats.laws.discipline.SemigroupalTests.Isomorphisms
import cats.laws.discipline._
import org.scalacheck.{Arbitrary, Cogen}
import org.scalacheck.Prop.forAll
import otters.laws.AsyncStreamLaws

trait AsyncStreamTests[F[_], G[_]] extends StreamTests[F] {
  override def laws: AsyncStreamLaws[F, G]

  def asyncStream[A: Arbitrary: Eq, B: Arbitrary: Eq, C: Arbitrary: Eq](
    implicit
    ArbFA: Arbitrary[F[A]],
    ArbFB: Arbitrary[F[B]],
    ArbFC: Arbitrary[F[C]],
    ArbAtoB: Arbitrary[A => B],
    ArbBtoGB: Arbitrary[B => G[B]],
    ArbFAtoB: Arbitrary[F[A => B]],
    ArbFBtoC: Arbitrary[F[B => C]],
    CogenA: Cogen[A],
    CogenB: Cogen[B],
    CogenC: Cogen[C],
    EqFA: Eq[F[A]],
    EqFB: Eq[F[B]],
    EqFC: Eq[F[C]],
    EqFInt: Eq[F[Int]],
    EqFABC: Eq[F[(A, B, C)]],
    EqFSeqA: Eq[F[Seq[A]]],
    iso: Isomorphisms[F]
  ): RuleSet =
    new DefaultRuleSet(
      name = "async stream",
      parent = Some(stream[A, B, C]),
      "map async associativity" -> forAll(laws.mapAsyncAssociativity[A, B] _),
      "map async n associativity" -> forAll(laws.mapAsyncNAssociativity[A, B] _)
    )
}

object AsyncStreamTests {
  def apply[F[_], G[_]](implicit ev: AsyncStreamLaws[F, G]): AsyncStreamTests[F, G] = new AsyncStreamTests[F, G] {
    override def laws: AsyncStreamLaws[F, G] = ev
  }
}
