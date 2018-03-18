package otters.syntax

import cats.data.{Writer, WriterT}
import cats.kernel.Semigroup
import cats.syntax.functor._
import cats.{Applicative, Functor, Id, Monoid, Semigroupal}
import otters._

import scala.collection.immutable
import scala.concurrent.duration.FiniteDuration

trait WriterTSyntax {

  implicit class WriterTStreamOps[F[_], L, A](override val stream: WriterT[F, L, A])
      extends WriterTStreamGrouped[F, L, A]
      with WriterTStreamAsync[F, L, A]
      with WriterTStreamConcatOps[F, L, A]

  implicit class WriterTStreamApply[F[_], A](stream: F[A]) {
    def toWriter[L: Monoid](implicit F: Applicative[F]): WriterT[F, L, A] = WriterT.liftF(stream)
    def toWriter[L](initial: L)(implicit F: Functor[F]): WriterT[F, L, A] = WriterT(stream.map(initial -> _))
  }

  implicit class WriterTStreamApplyTuple[F[_], L, A](stream: F[(L, A)]) {
    def toWriter: WriterT[F, L, A] = WriterT(stream)
  }
}

trait WriterTExtendedSyntax[P[_, _], S[_, _]] {
  implicit class WriterTStreamExtOps[F[_], L, A](override val stream: WriterT[F, L, A])
      extends WriterTStreamPipeSink[F, P, S, L, A]
}

trait WriterTStreamGrouped[F[_], L, A] {
  import SeqInstances._

  def stream: WriterT[F, L, A]

  def groupedWithin(
    n: Int,
    d: FiniteDuration
  )(implicit F: Stream[F], ev: Applicative[Writer[L, ?]]): WriterT[F, L, Seq[A]] =
    WriterT(F.groupedWithin(stream.run)(n, d).map(las => seqTraverse.sequence(las.map(WriterT[Id, L, A])).run))

  def grouped(n: Int)(implicit F: Stream[F], ev: Applicative[Writer[L, ?]]): WriterT[F, L, Seq[A]] =
    WriterT(F.grouped(stream.run)(n).map(las => seqTraverse.sequence(las.map(WriterT[Id, L, A])).run))
}

trait WriterTStreamPipeSink[F[_], P[_, _], S[_, _], L, A] {
  def stream: WriterT[F, L, A]

  def toSinks[G[_], H[_], B, C, D](lSink: S[L, B], rSink: S[A, C])(
    combine: (B, C) => D
  )(implicit F: TupleStream[F, G, H, P, S]): H[D] =
    F.toSinks[L, A, B, C, D](stream.run)(lSink, rSink)(combine)

  def toSinksTupled[G[_], H[_], B, C](lSink: S[L, B], rSink: S[A, C])(
    implicit F: TupleStream[F, G, H, P, S]
  ): H[(B, C)] =
    F.toSinks[L, A, B, C](stream.run)(lSink, rSink)

  def via[G[_], H[_], B, C](lPipe: P[L, B], rPipe: P[A, C])(implicit F: TupleStream[F, G, H, P, S]): WriterT[F, B, C] =
    WriterT(F.fanOutFanIn(stream.run)(lPipe, rPipe))

  def dataVia[G[_], H[_], B](dataPipe: P[A, B])(implicit F: TupleStream[F, G, H, P, S]): WriterT[F, L, B] =
    WriterT(F.tupleRightVia(stream.run)(dataPipe))

  def stateVia[G[_], H[_], M](statePipe: P[L, M])(implicit F: TupleStream[F, G, H, P, S]): WriterT[F, M, A] =
    WriterT(F.tupleLeftVia(stream.run)(statePipe))
}

trait WriterTStreamAsync[F[_], L, A] {
  def stream: WriterT[F, L, A]

  def mapAsync[G[_], B](f: A => G[B])(implicit F: AsyncStream[F, G], G: Functor[G]): WriterT[F, L, B] =
    WriterT(F.mapAsync(stream.run)(doMapAsync(f).tupled))

  def mapAsync[G[_], B](
    parallelism: Int
  )(f: A => G[B])(implicit F: AsyncStream[F, G], G: Functor[G]): WriterT[F, L, B] =
    WriterT(F.mapAsyncN(stream.run)(parallelism: Int)(doMapAsync(f).tupled))

  private def doMapAsync[G[_], B](f: A => G[B])(implicit G: Functor[G]): (L, A) => G[(L, B)] =
    (l, a) => f(a).map(l -> _)

  def flatMapAsync[G[_], B](
    f: A => WriterT[G, L, B]
  )(implicit F: AsyncStream[F, G], G: Functor[G], L: Semigroup[L]): WriterT[F, L, B] =
    WriterT(F.mapAsync(stream.run)(doFlatMapAsync(f).tupled))

  def flatMapAsync[G[_], B](
    parallelism: Int
  )(f: A => WriterT[G, L, B])(implicit F: AsyncStream[F, G], G: Functor[G], L: Semigroup[L]): WriterT[F, L, B] =
    WriterT(F.mapAsyncN(stream.run)(parallelism)(doFlatMapAsync(f).tupled))

  private def doFlatMapAsync[G[_], B](
    f: A => WriterT[G, L, B]
  )(implicit G: Functor[G], L: Semigroup[L]): (L, A) => G[(L, B)] =
    (l, a) => f(a).mapWritten(L.combine(_, l)).run

  def mapBothAsync[G[_], M, B](f: (L, A) => G[(M, B)])(implicit F: AsyncStream[F, G], G: Functor[G]): WriterT[F, M, B] =
    WriterT(F.mapAsync(stream.run)(f.tupled))

  def mapBothAsync[G[_], M, B](
    parallelism: Int
  )(f: (L, A) => G[(M, B)])(implicit F: AsyncStream[F, G], G: Functor[G]): WriterT[F, M, B] =
    WriterT(F.mapAsyncN(stream.run)(parallelism)(f.tupled))

  def bimapAsync[G[_], M, B](
    f: L => G[M],
    g: A => G[B]
  )(implicit F: AsyncStream[F, G], G: Semigroupal[G]): WriterT[F, M, B] =
    WriterT(F.mapAsync(stream.run)(doBimapAsync(f, g).tupled))

  def bimapAsync[G[_], M, B](
    parallelism: Int
  )(f: L => G[M], g: A => G[B])(implicit F: AsyncStream[F, G], G: Semigroupal[G]): WriterT[F, M, B] =
    WriterT(F.mapAsyncN(stream.run)(parallelism)(doBimapAsync(f, g).tupled))

  def doBimapAsync[G[_], M, B](f: L => G[M], g: A => G[B])(implicit G: Semigroupal[G]): (L, A) => G[(M, B)] =
    (l, a) => G.product(f(l), g(a))
}

trait WriterTStreamConcatOps[F[_], L, A] extends WriterConcatOps {
  def stream: WriterT[F, L, A]

  def mapConcat[B](
    f: A => immutable.Iterable[B],
    f2: ((L, immutable.Iterable[B])) => immutable.Iterable[(L, B)]
  )(implicit F: Stream[F], L: Monoid[L]): WriterT[F, L, B] =
    WriterT(F.mapConcat(stream.run) { case (l, a) => f2(l -> f(a)) })

  def safeMapConcat[B](
    f: A => immutable.Iterable[B],
    f2: ((L, immutable.Iterable[Option[B]])) => immutable.Iterable[(L, Option[B])]
  )(implicit F: Stream[F], L: Monoid[L]): WriterT[F, L, Option[B]] =
    WriterT(F.mapConcat(stream.run) { case (l, a) => f2(l -> makeSafe(f(a))) })

  def mapConcatHead[B](f: A => immutable.Iterable[B])(implicit F: Stream[F], L: Monoid[L]): WriterT[F, L, B] =
    mapConcat[B](f, head[L, B])

  def safeMapConcatHead[B](
    f: A => immutable.Iterable[B]
  )(implicit F: Stream[F], L: Monoid[L]): WriterT[F, L, Option[B]] =
    safeMapConcat[B](f, head[L, Option[B]])

  def mapConcatTail[B](f: A => immutable.Iterable[B])(implicit F: Stream[F], L: Monoid[L]): WriterT[F, L, B] =
    mapConcat[B](f, tail[L, B])

  def safeMapConcatTail[B](
    f: A => immutable.Iterable[B]
  )(implicit F: Stream[F], L: Monoid[L]): WriterT[F, L, Option[B]] =
    safeMapConcat[B](f, tail[L, Option[B]])

  def mapConcatAll[B](f: A => immutable.Iterable[B])(implicit F: Stream[F], L: Monoid[L]): WriterT[F, L, B] =
    mapConcat[B](f, all[L, B])

  def safeMapConcatAll[B](
    f: A => immutable.Iterable[B]
  )(implicit F: Stream[F], L: Monoid[L]): WriterT[F, L, Option[B]] =
    safeMapConcat[B](f, all[L, Option[B]])
}

trait WriterConcatOps {
  protected def makeSafe[A](cs: immutable.Iterable[A]): immutable.Iterable[Option[A]] =
    if (cs.isEmpty) Vector(None)
    else cs.map(Some(_))

  def head[S: Monoid, A]: ((S, immutable.Iterable[A])) => immutable.Iterable[(S, A)] = {
    case (state, data) =>
      data match {
        case d: immutable.Seq[A] => headSeq[S, A](state, d)
        case d => headSeq[S, A](state, d.toVector)
      }
  }

  def tail[S: Monoid, A]: ((S, immutable.Iterable[A])) => immutable.Iterable[(S, A)] = {
    case (state, data) =>
      data match {
        case d: immutable.Seq[A] => tailSeq[S, A](state, d)
        case d => tailSeq[S, A](state, d.toVector)
      }
  }

  def all[S: Monoid, A]: ((S, immutable.Iterable[A])) => immutable.Iterable[(S, A)] = {
    case (state, data) =>
      data match {
        case immutable.Seq() => immutable.Seq.empty
        case d: immutable.Seq[A] => allSeq[S, A](state, d)
        case d => allSeq[S, A](state, d.toVector)
      }
  }

  def headSeq[S, A](state: S, as: immutable.Seq[A])(implicit S: Monoid[S]): immutable.Seq[(S, A)] =
    as match {
      case immutable.Seq() => immutable.Seq.empty
      case head +: tail =>
        (state, head) +: tail.map(a => (S.empty, a))
    }

  def tailSeq[S, A](state: S, as: immutable.Seq[A])(implicit S: Monoid[S]): immutable.Seq[(S, A)] =
    as match {
      case immutable.Seq() => immutable.Seq.empty
      case xs :+ last =>
        xs.map(a => (S.empty, a)) :+ (state -> last)
    }

  def allSeq[S, A](state: S, as: immutable.Seq[A]): immutable.Seq[(S, A)] =
    as.map(a => (state, a))
}
