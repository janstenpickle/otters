package otters.laws.discipline

import cats.Eq
import cats.laws.discipline.SemigroupalTests.Isomorphisms
import cats.laws.discipline._
import org.scalacheck.Prop.forAll
import org.scalacheck.{Arbitrary, Cogen}
import otters.laws.TupleStreamLaws
import otters.{Pipe, Sink}

trait TupleStreamTests[F[_], G[_], H[_]] extends StreamSinkTests[F, H, G] {
  def laws: TupleStreamLaws[F, G, H]

  def tupleStream[A: Arbitrary, B: Arbitrary, C: Arbitrary](
    implicit
    ArbFA: Arbitrary[F[A]],
    ArbFB: Arbitrary[F[B]],
    ArbFC: Arbitrary[F[C]],
    ArbFAB: Arbitrary[F[(A, B)]],
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
    EqFAB: Eq[F[(A, B)]],
    EqGB: Eq[H[G[List[B]]]],
    EqFABC: Eq[F[(A, B, C)]],
    EqGAB: Eq[H[(G[List[A]], G[List[B]])]],
    EqFSeqA: Eq[F[Seq[A]]],
    iso: Isomorphisms[F]
  ): RuleSet =
    new DefaultRuleSet(
      name = "tuple stream",
      parent = Some(streamSink[A, B, C]),
      "fan out fan in identity" -> forAll(laws.fanOutFanInIdentity[A, B] _),
      "left via identity" -> forAll(laws.leftViaIdentity[A, B] _),
      "right via identity" -> forAll(laws.rightViaIdentity[A, B] _),
      "to sinks associativity" -> forAll(laws.toSinksAssociativity[A, B] _)
    )
}

object TupleStreamTests {
  def apply[F[_], G[_], H[_]](implicit ev: TupleStreamLaws[F, G, H]): TupleStreamTests[F, G, H] =
    new TupleStreamTests[F, G, H] {
      override def laws: TupleStreamLaws[F, G, H] = ev
    }
}
