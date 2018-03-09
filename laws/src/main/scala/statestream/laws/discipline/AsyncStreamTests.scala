package statestream.laws.discipline

import cats.Eq
import cats.laws.discipline._
import org.scalacheck.Arbitrary
import org.scalacheck.Prop.forAll
import org.typelevel.discipline.Laws
import statestream.laws.AsyncStreamLaws

trait AsyncStreamTests[F[_], G[_]] extends Laws {
  def laws: AsyncStreamLaws[F, G]

  def asyncStream[A: Arbitrary, B: Arbitrary](
    implicit
    ArbFA: Arbitrary[F[A]],
    ArbAtoB: Arbitrary[A => B],
    ArbBtoGB: Arbitrary[B => G[B]],
    EqFB: Eq[F[B]]
  ): RuleSet =
    new DefaultRuleSet(
      name = "async stream",
      parent = None,
      "map async associativity" -> forAll(laws.mapAsyncAssociativity[A, B] _),
      "map async n associativity" -> forAll(laws.mapAsyncNAssociativity[A, B] _)
    )
}

object AsyncStreamTests {
  def apply[F[_], G[_]](implicit ev: AsyncStreamLaws[F, G]): AsyncStreamTests[F, G] = new AsyncStreamTests[F, G] {
    override def laws: AsyncStreamLaws[F, G] = ev
  }
}
