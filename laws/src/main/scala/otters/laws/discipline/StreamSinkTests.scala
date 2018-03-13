package otters.laws.discipline

import cats.Eq
import cats.laws.discipline.SemigroupalTests.Isomorphisms
import cats.laws.discipline._
import org.scalacheck.Prop.forAll
import org.scalacheck.{Arbitrary, Cogen}
import otters.laws.StreamSinkLaws
import otters.{Pipe, Sink}

trait StreamSinkTests[F[_], G[_], H[_]] extends StreamTests[F] {
  def laws: StreamSinkLaws[F, G, H]

  def streamSink[A: Arbitrary: Eq, B: Arbitrary: Eq, C: Arbitrary: Eq](
    implicit
    ArbFA: Arbitrary[F[A]],
    ArbFB: Arbitrary[F[B]],
    ArbFC: Arbitrary[F[C]],
    ArbFAtoB: Arbitrary[F[A => B]],
    ArbFBtoC: Arbitrary[F[B => C]],
    ArbPipeAToB: Arbitrary[Pipe[F, A, B]],
    ArbPipeBToC: Arbitrary[Pipe[F, B, C]],
    ArbSinkAToB: Arbitrary[Sink[F, G, A, H[List[B]]]],
    CogenA: Cogen[A],
    CogenB: Cogen[B],
    CogenC: Cogen[C],
    EqFA: Eq[F[A]],
    EqFB: Eq[F[B]],
    EqFC: Eq[F[C]],
    EqFInt: Eq[F[Int]],
    EqGB: Eq[G[H[List[B]]]],
    EqFABC: Eq[F[(A, B, C)]],
    EqFSeqA: Eq[F[Seq[A]]],
    iso: Isomorphisms[F]
  ): RuleSet =
    new DefaultRuleSet(
      name = "stream sink",
      parent = Some(stream[A, B, C]),
      "sink application" -> forAll(laws.sinkApplication[A, B] _)
    )
}

object StreamSinkTests {
  def apply[F[_], G[_], H[_]](implicit ev: StreamSinkLaws[F, G, H]): StreamSinkTests[F, G, H] =
    new StreamSinkTests[F, G, H] {
      override def laws: StreamSinkLaws[F, G, H] = ev
    }
}
