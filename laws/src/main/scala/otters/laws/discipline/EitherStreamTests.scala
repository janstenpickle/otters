package otters.laws.discipline

import cats.Eq
import cats.laws.discipline._
import cats.laws.discipline.SemigroupalTests.Isomorphisms
import org.scalacheck.Prop.forAll
import org.scalacheck.{Arbitrary, Cogen}
import otters.laws.EitherStreamLaws
import otters.{Pipe, Sink}

trait EitherStreamTests[F[_], G[_], H[_]] extends TupleStreamTests[F, G, H] {
  override def laws: EitherStreamLaws[F, G, H]

  def eitherStream[A: Arbitrary: Eq, B: Arbitrary: Eq, C: Arbitrary: Eq](
    implicit
    ArbFA: Arbitrary[F[A]],
    ArbFB: Arbitrary[F[B]],
    ArbFC: Arbitrary[F[C]],
    ArbFAB: Arbitrary[F[(A, B)]],
    ArbFEitherAB: Arbitrary[F[Either[A, B]]],
    ArbFAtoB: Arbitrary[F[A => B]],
    ArbFBtoC: Arbitrary[F[B => C]],
    ArbPipeAToB: Arbitrary[Pipe[F, A, B]],
    ArbPipeBToC: Arbitrary[Pipe[F, B, C]],
    ArbSinkAToA: Arbitrary[Sink[F, H, A, G[List[A]]]],
    ArbSinkBToB: Arbitrary[Sink[F, H, B, G[List[B]]]],
    ArbSinkAToB: Arbitrary[Sink[F, H, A, G[List[B]]]],
    ArbSinkABToAB: Arbitrary[Sink[F, H, (A, B), G[List[(A, B)]]]],
    CogenA: Cogen[A],
    CogenB: Cogen[B],
    CogenC: Cogen[C],
    EqFA: Eq[F[A]],
    EqFB: Eq[F[B]],
    EqFC: Eq[F[C]],
    EqFInt: Eq[F[Int]],
    EqFAB: Eq[F[(A, B)]],
    EqFEitherAB: Eq[F[Either[A, B]]],
    EqGA: Eq[H[G[List[A]]]],
    EqGB: Eq[H[G[List[B]]]],
    EqFABC: Eq[F[(A, B, C)]],
    EqGAB: Eq[H[(G[List[A]], G[List[B]])]],
    EqFSeqA: Eq[F[Seq[A]]],
    iso: Isomorphisms[F]
  ): RuleSet =
    new DefaultRuleSet(
      name = "either stream",
      parent = Some(tupleStream[A, B, C]),
      "via either identity" -> forAll(laws.viaIdentity[A, B] _),
      "left via either identity" -> forAll(laws.leftViaIdentity[A, B] _),
      "right via either identity" -> forAll(laws.rightViaIdentity[A, B] _),
      "to sinks associativity" -> forAll(laws.toEitherSinksAssociativity[A, B] _),
      "left to associativity" -> forAll(laws.toLeftAssociativity[A, B] _),
      "right to associativity" -> forAll(laws.toRightAssociativity[A, B] _)
    )
}

object EitherStreamTests {
  def apply[F[_], G[_], H[_]](implicit ev: EitherStreamLaws[F, G, H]): EitherStreamTests[F, G, H] =
    new EitherStreamTests[F, G, H] {
      override def laws: EitherStreamLaws[F, G, H] = ev
    }
}
