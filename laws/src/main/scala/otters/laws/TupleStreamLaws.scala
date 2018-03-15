package otters.laws

import cats.kernel.laws.IsEq
import cats.laws._
import cats.syntax.functor._
import cats.{FlatMap, Functor}
import otters.{Sink, TupleStream}

trait TupleStreamLaws[F[_], G[_], H[_]] extends StreamSinkLaws[F, H, G] {
  implicit override def F: TupleStream[F, G, H]
  implicit def G: Functor[G]
  implicit def H: Functor[H]

  def fanOutFanInIdentity[A, B](fab: F[(A, B)]): IsEq[F[(A, B)]] =
    F.fanOutFanIn(fab)(identity, identity) <-> fab

  def tupleLeftViaIdentity[A, B](fab: F[(A, B)]): IsEq[F[(A, B)]] =
    F.tupleLeftVia(fab)(identity) <-> fab

  def tupleRightViaIdentity[A, B](fab: F[(A, B)]): IsEq[F[(A, B)]] =
    F.tupleRightVia(fab)(identity) <-> fab

  def toSinksAssociativity[A, B](
    fab: F[(A, B)],
    sab: Sink[F, H, (A, B), G[List[(A, B)]]],
    sa: Sink[F, H, A, G[List[A]]],
    sb: Sink[F, H, B, G[List[B]]]
  ): IsEq[H[(G[List[A]], G[List[B]])]] =
    F.to(fab)(sab).map(g => (g.map(_.map(_._1)), g.map(_.map(_._2)))) <-> F
      .toSinks[A, B, G[List[A]], G[List[B]]](fab)(sa, sb)
}

object TupleStreamLaws {
  def apply[F[_], G[_], H[_]](
    implicit ev: TupleStream[F, G, H],
    ev1: FlatMap[G],
    ev2: Functor[H]
  ): TupleStreamLaws[F, G, H] =
    new TupleStreamLaws[F, G, H] {
      implicit override def F: TupleStream[F, G, H] = ev
      override implicit def G: FlatMap[G] = ev1
      override implicit def H: Functor[H] = ev2
    }
}
