package otters.laws

import cats.laws.{IsEq, _}
import cats.{FlatMap, Functor}
import otters.{EitherStream, Sink}
import cats.syntax.functor._

trait EitherStreamLaws[F[_], G[_], H[_]] extends TupleStreamLaws[F, G, H] {
  implicit override def F: EitherStream[F, G, H]

  def viaIdentity[A, B](fab: F[Either[A, B]]): IsEq[F[Either[A, B]]] =
    F.via(fab)(identity, identity) <-> fab

  def leftViaIdentity[A, B](fab: F[Either[A, B]]): IsEq[F[Either[A, B]]] =
    F.leftVia(fab)(identity) <-> fab

  def rightViaIdentity[A, B](fab: F[Either[A, B]]): IsEq[F[Either[A, B]]] =
    F.rightVia(fab)(identity) <-> fab

  def toLeftAssociativity[A, B](
    fa: F[A],
    aSink: Sink[F, H, A, G[List[A]]],
    bSink: Sink[F, H, B, G[List[B]]]
  ): IsEq[H[G[List[A]]]] =
    F.toEitherSinks[A, B, G[List[A]], G[List[B]], G[List[A]]](fa.map[Either[A, B]](Left(_)))(aSink, bSink)(
      (as, _) => as
    ) <-> F.to(fa)(aSink)

  def toRightAssociativity[A, B](
    fa: F[B],
    aSink: Sink[F, H, A, G[List[A]]],
    bSink: Sink[F, H, B, G[List[B]]]
  ): IsEq[H[G[List[B]]]] =
    F.toEitherSinks[A, B, G[List[A]], G[List[B]], G[List[B]]](fa.map[Either[A, B]](Right(_)))(aSink, bSink)(
      (_, bs) => bs
    ) <-> F.to(fa)(bSink)

  def toEitherSinksAssociativity[A, B](
    fa: List[Either[A, B]],
    aSink: Sink[F, H, A, G[List[A]]],
    bSink: Sink[F, H, B, G[List[B]]]
  ): IsEq[H[(G[List[A]], G[List[B]])]] =
    F.toEitherSinks[A, B, G[List[A]], G[List[B]]](F.fromSeq(fa))(aSink, bSink) <-> F.H
      .product(F.to(F.fromSeq(fa.collect { case Left(a) => a }))(aSink), F.to(F.fromSeq(fa.collect {
        case Right(b) => b
      }))(bSink))
}

object EitherStreamLaws {
  def apply[F[_], G[_], H[_]](
    implicit ev: EitherStream[F, G, H],
    ev1: FlatMap[G],
    ev2: Functor[H]
  ): EitherStreamLaws[F, G, H] =
    new EitherStreamLaws[F, G, H] {
      implicit override def F: EitherStream[F, G, H] = ev
      override implicit def G: FlatMap[G] = ev1
      override implicit def H: Functor[H] = ev2
    }
}
