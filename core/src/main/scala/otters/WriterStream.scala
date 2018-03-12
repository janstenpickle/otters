package otters

import cats.data.WriterT
import cats.kernel.Semigroup
import cats.syntax.applicative._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.{~>, Always, Applicative, Comonad, Eval, FlatMap, Functor, Monoid, Traverse}
import otters.syntax.stream._

import scala.collection.immutable
import scala.concurrent.duration.FiniteDuration

trait WriterStreamBase[F[_], G[_], S, T] {
  type Return[SC, A]

  val stream: F[WriterT[G, S, T]]

  protected def apply[SC, A](run: F[WriterT[G, SC, A]]): Return[SC, A]

  def map[A](f: T => A)(implicit F: Functor[F], G: Functor[G]): Return[S, A] = apply(stream.map(_.map(f)))

  def flatMapWriter[A](f: T => WriterT[G, S, A])(implicit F: Functor[F], G: FlatMap[G], S: Semigroup[S]): Return[S, A] =
    apply(stream.map(_.flatMap(f)))

  def mapBoth[SA, A](f: (S, T) => (SA, A))(implicit F: Functor[F], G: Functor[G]): Return[SA, A] =
    apply(stream.map(_.mapBoth(f)))

  def flatMapBoth[A, SA](f: (S, T) => G[(SA, A)])(implicit F: Functor[F], G: FlatMap[G]): Return[SA, A] =
    apply(stream.map(t => WriterT(t.run.flatMap(f.tupled))))

  def mapWritten[SA](f: S => SA)(implicit F: Functor[F], G: Functor[G]): Return[SA, T] =
    apply(stream.map(_.mapWritten(f)))

  def bimap[SA, A](f: S => SA, g: T => A)(implicit F: Functor[F], G: Functor[G]): Return[SA, A] =
    apply(stream.map(_.bimap(f, g)))

  def swap(implicit F: Functor[F], G: Functor[G]): Return[T, S] = apply(stream.map(_.swap))
}

trait WithComonad[G[_]] {
  protected def CG: Comonad[G]
}

trait WithNat[G[_], H[_]] {
  protected def nat: G ~> H
}

trait WriterStreamTupleBase[F[_], G[_], H[_], S, T] {
  def toTuple(implicit F: AsyncStream[F, H]): F[(S, T)]
}

trait WriterStreamTupleComonad[F[_], G[_], H[_], S, T] {
  self: WriterStreamTupleBase[F, G, H, S, T] with WithComonad[G] with WriterStreamBase[F, G, S, T] =>

  def toTuple(implicit F: AsyncStream[F, H]): F[(S, T)] =
    stream.map(d => CG.extract(d.run))
}

trait WriterStreamTupleNat[F[_], G[_], H[_], S, T] {
  self: WriterStreamTupleBase[F, G, H, S, T] with WithNat[G, H] with WriterStreamBase[F, G, S, T] =>

  def toTuple(implicit F: AsyncStream[F, H]): F[(S, T)] =
    F.mapAsync(stream)(d => nat(d.run))
}

trait WriterStreamStreamOps[F[_], G[_], H[_], I[_], S, T] {
  self: WriterStreamBase[F, G, S, T] with WriterStreamTupleBase[F, G, H, S, T] =>

  def via[SA, A](
    statePipe: Pipe[F, S, SA],
    dataPipe: Pipe[F, T, A]
  )(implicit F: TupleStream[F, H, I], G: Applicative[G]): Return[SA, A] =
    apply(F.fanOutFanIn(toTuple)(statePipe, dataPipe).map(sa => WriterT(G.pure(sa))))

  def stateVia[SC](statePipe: Pipe[F, S, SC])(implicit F: TupleStream[F, H, I], G: Applicative[G]): Return[SC, T] =
    via(statePipe, identity)

  def dataVia[B](dataPipe: Pipe[F, T, B])(implicit F: TupleStream[F, H, I], G: Applicative[G]): Return[S, B] =
    via(identity, dataPipe)

  def toSinks[A, B, C](stateSink: Sink[F, I, S, A], dataSink: Sink[F, I, T, B])(
    combine: (A, B) => C,
  )(implicit F: TupleStream[F, H, I], G: FlatMap[G]): I[C] =
    F.toSinks[S, T, A, B, C](toTuple)(stateSink, dataSink)(combine)

  def toSinks[A, B](
    stateSink: Sink[F, I, S, A],
    dataSink: Sink[F, I, T, B]
  )(implicit F: TupleStream[F, H, I], G: FlatMap[G]): I[(A, B)] =
    toSinks[A, B, (A, B)](stateSink, dataSink)(_ -> _)
}

trait SeqInstances {
  implicit val seqTraverse: Traverse[Seq] = new Traverse[Seq] {

    override def traverse[G[_], A, B](fa: Seq[A])(f: A => G[B])(implicit G: Applicative[G]): G[Seq[B]] =
      foldRight[A, G[Seq[B]]](fa, Always(G.pure(Seq.empty))) { (a, lglb) =>
        G.map2Eval(f(a), lglb)(_ +: _)
      }.value

    override def foldLeft[A, B](fa: Seq[A], b: B)(f: (B, A) => B): B = fa.foldLeft(b)(f)

    override def foldRight[A, B](fa: Seq[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] = {
      def loop(as: Seq[A]): Eval[B] =
        as match {
          case Seq() => lb
          case h +: t => f(h, Eval.defer(loop(t)))
        }
      Eval.defer(loop(fa))
    }
  }
}

trait WriterStreamGrouped[F[_], G[_], S, T] extends SeqInstances {
  self: WriterStreamBase[F, G, S, T] =>

  def groupedWithin(
    n: Int,
    d: FiniteDuration
  )(implicit F: Stream[F], G: Applicative[WriterT[G, S, ?]]): Return[S, Seq[T]] =
    apply(stream.groupedWithin(n, d).map(seqTraverse.sequence(_)))

  def group(n: Int)(implicit F: Stream[F], G: Applicative[WriterT[G, S, ?]]): Return[S, Seq[T]] =
    apply(stream.grouped(n).map(seqTraverse.sequence(_)))
}

trait WriterStreamAsync[F[_], G[_], H[_], S, T] { self: WriterStreamBase[F, G, S, T] =>
  def mapAsync[A](
    parallelism: Int
  )(f: T => H[A])(implicit F: AsyncStream[F, H], G: Applicative[G], H: FlatMap[H], nat: G ~> H): Return[S, A] =
    asyncTransform(parallelism)(w => WriterT(w.run.flatMap { case (s, t) => f(t).map(s -> _) }))

  def flatMapWriterAsync[SC, A](parallelism: Int)(
    f: T => WriterT[H, S, A]
  )(implicit F: AsyncStream[F, H], G: Applicative[G], H: FlatMap[H], S: Semigroup[S], nat: G ~> H): Return[S, A] =
    asyncTransform(parallelism)(_.flatMap(f))

  private def asyncTransform[A](parallelism: Int)(
    f: WriterT[H, S, T] => WriterT[H, S, A]
  )(implicit F: AsyncStream[F, H], G: Applicative[G], H: FlatMap[H], nat: G ~> H): Return[S, A] =
    apply(F.mapAsyncN(stream)(parallelism)(x => f(x.mapK[H](nat)).run.map(sc => WriterT(G.pure(sc)))))
}

trait WriterConcatOps {
  protected def makeSafe[C](cs: immutable.Iterable[C]): immutable.Iterable[Option[C]] =
    if (cs.isEmpty) Vector(None)
    else cs.map(Some(_))

  def head[F[_]: Applicative, S: Monoid, A]: ((S, immutable.Iterable[A])) => immutable.Iterable[WriterT[F, S, A]] = {
    case (state, data) =>
      data match {
        case d: immutable.Seq[A] => headSeq[F, S, A](state, d)
        case d => headSeq[F, S, A](state, d.toVector)
      }
  }

  def tail[F[_]: Applicative, S: Monoid, A]: ((S, immutable.Iterable[A])) => immutable.Iterable[WriterT[F, S, A]] = {
    case (state, data) =>
      data match {
        case d: immutable.Seq[A] => tailSeq[F, S, A](state, d)
        case d => tailSeq[F, S, A](state, d.toVector)
      }
  }

  def all[F[_]: Applicative, S: Monoid, A]: ((S, immutable.Iterable[A])) => immutable.Iterable[WriterT[F, S, A]] = {
    case (state, data) =>
      data match {
        case immutable.Seq() => immutable.Seq.empty
        case d: immutable.Seq[A] => allSeq[F, S, A](state, d)
        case d => allSeq[F, S, A](state, d.toVector)
      }
  }

  def headSeq[F[_]: Applicative, S, A](state: S, as: immutable.Seq[A])(
    implicit S: Monoid[S]
  ): immutable.Seq[WriterT[F, S, A]] =
    as match {
      case immutable.Seq() => immutable.Seq.empty
      case head +: tail =>
        WriterT[F, S, A]((state, head).pure) +: tail.map(a => WriterT[F, S, A]((S.empty, a).pure))
    }

  def tailSeq[F[_]: Applicative, S, A](state: S, as: immutable.Seq[A])(
    implicit S: Monoid[S]
  ): immutable.Seq[WriterT[F, S, A]] =
    as match {
      case immutable.Seq() => immutable.Seq.empty
      case xs :+ last =>
        xs.map(a => WriterT[F, S, A]((S.empty, a).pure)) :+ WriterT[F, S, A]((state, last).pure)
    }

  def allSeq[F[_]: Applicative, S, A](state: S, as: immutable.Seq[A]): immutable.Seq[WriterT[F, S, A]] =
    as.map(a => WriterT[F, S, A]((state, a).pure))
}

trait WriterStreamConcatBase[F[_], G[_], H[_], S, T] extends WriterConcatOps {
  self: WriterStreamBase[F, G, S, T] =>

  def mapConcat[A](
    f: T => immutable.Iterable[A],
    f2: ((S, immutable.Iterable[A])) => immutable.Iterable[WriterT[G, S, A]]
  )(implicit F: AsyncStream[F, H], G: Applicative[G], S: Monoid[S]): Return[S, A]

  def safeMapConcat[A](
    f: T => immutable.Iterable[A],
    f2: ((S, immutable.Iterable[Option[A]])) => immutable.Iterable[WriterT[G, S, Option[A]]]
  )(implicit F: AsyncStream[F, H], G: Applicative[G], S: Monoid[S]): Return[S, Option[A]]

  def mapConcatHead[A](
    f: T => immutable.Iterable[A]
  )(implicit F: AsyncStream[F, H], G: Applicative[G], S: Monoid[S]): Return[S, A] =
    mapConcat[A](f, head[G, S, A])

  def safeMapConcatHead[A](
    f: T => immutable.Iterable[A],
  )(implicit F: AsyncStream[F, H], G: Applicative[G], S: Monoid[S]): Return[S, Option[A]] =
    safeMapConcat[A](f, head[G, S, Option[A]])

  def mapConcatTail[A](
    f: T => immutable.Iterable[A]
  )(implicit F: AsyncStream[F, H], G: Applicative[G], S: Monoid[S]): Return[S, A] =
    mapConcat[A](f, tail[G, S, A])

  def safeMapConcatTail[A](
    f: T => immutable.Iterable[A],
  )(implicit F: AsyncStream[F, H], G: Applicative[G], S: Monoid[S]): Return[S, Option[A]] =
    safeMapConcat[A](f, tail[G, S, Option[A]])

  def mapConcatAll[A](
    f: T => immutable.Iterable[A]
  )(implicit F: AsyncStream[F, H], G: Applicative[G], S: Monoid[S]): Return[S, A] =
    mapConcat[A](f, all[G, S, A])

  def safeMapConcatAll[A](
    f: T => immutable.Iterable[A],
  )(implicit F: AsyncStream[F, H], G: Applicative[G], S: Monoid[S]): Return[S, Option[A]] =
    safeMapConcat[A](f, all[G, S, Option[A]])
}

trait WriterStreamConcatComonad[F[_], G[_], H[_], S, T] extends WriterStreamConcatBase[F, G, H, S, T] {
  self: WriterStreamBase[F, G, S, T] with WithComonad[G] =>

  override def mapConcat[A](
    f: T => immutable.Iterable[A],
    f2: ((S, immutable.Iterable[A])) => immutable.Iterable[WriterT[G, S, A]]
  )(implicit F: AsyncStream[F, H], G: Applicative[G], S: Monoid[S]): Return[S, A] =
    apply(stream.mapConcat(x => CG.extract(x.map(f).run.map(f2))))

  override def safeMapConcat[C](
    f: T => immutable.Iterable[C],
    f2: ((S, immutable.Iterable[Option[C]])) => immutable.Iterable[WriterT[G, S, Option[C]]]
  )(implicit F: AsyncStream[F, H], G: Applicative[G], S: Monoid[S]): Return[S, Option[C]] =
    apply(stream.mapConcat(x => CG.extract(x.map(b => makeSafe(f(b))).run.map(f2))))
}

trait WriterStreamConcatNat[F[_], G[_], H[_], S, T] extends WriterStreamConcatBase[F, G, H, S, T] {
  self: WriterStreamBase[F, G, S, T] with WithNat[G, H] =>

  def mapConcat[B](
    f: T => immutable.Iterable[B],
    f2: ((S, immutable.Iterable[B])) => immutable.Iterable[WriterT[G, S, B]]
  )(implicit F: AsyncStream[F, H], G: Applicative[G], S: Monoid[S]): Return[S, B] =
    apply(
      F.mapAsync(stream)(x => nat(x.map(f).run.map(f2)))
        .mapConcat(identity)
    )

  def safeMapConcat[B](
    f: T => immutable.Iterable[B],
    f2: ((S, immutable.Iterable[Option[B]])) => immutable.Iterable[WriterT[G, S, Option[B]]]
  )(implicit F: AsyncStream[F, H], G: Applicative[G], S: Monoid[S]): Return[S, Option[B]] =
    apply(
      F.mapAsync(stream)(x => nat(x.map(b => makeSafe(f(b))).run.map(f2)))
        .mapConcat(identity)
    )
}

trait WriterStream[F[_], G[_], H[_], I[_], S, T]
    extends WriterStreamBase[F, G, S, T]
    with WriterStreamAsync[F, G, H, S, T]
    with WriterStreamConcatBase[F, G, H, S, T]
    with WriterStreamTupleBase[F, G, H, S, T]
    with WriterStreamStreamOps[F, G, H, I, S, T]
    with WriterStreamGrouped[F, G, S, T] {
  override type Return[SA, B] <: WriterStream[F, G, H, I, SA, B]
}

case class WriterStreamComonad[F[_], G[_], H[_], I[_], S, T](stream: F[WriterT[G, S, T]])(
  override implicit protected val CG: Comonad[G]
) extends WriterStream[F, G, H, I, S, T]
    with WriterStreamConcatComonad[F, G, H, S, T]
    with WriterStreamTupleComonad[F, G, H, S, T]
    with WithComonad[G] {

  override type Return[SA, A] = WriterStreamComonad[F, G, H, I, SA, A]

  override protected def apply[S, A](src: F[WriterT[G, S, A]]): WriterStreamComonad[F, G, H, I, S, A] =
    new WriterStreamComonad[F, G, H, I, S, A](src)
}

object WriterStreamComonad {
  def apply[F[_]: Functor, G[_]: Applicative: Comonad, H[_], I[_], S, A](
    src: F[A],
    initial: S
  ): WriterStreamComonad[F, G, H, I, S, A] =
    new WriterStreamComonad[F, G, H, I, S, A](src.map(a => WriterT((initial, a).pure)))

  def apply[F[_]: Functor, G[_]: Applicative: Comonad, H[_], I[_], S: Monoid, A](
    src: F[A]
  ): WriterStreamComonad[F, G, H, I, S, A] =
    new WriterStreamComonad[F, G, H, I, S, A](src.map(a => WriterT.liftF(a.pure)))

  def apply[F[_]: Functor, G[_]: Applicative: Comonad, H[_], I[_], S, A](
    src: F[(S, A)]
  ): WriterStreamComonad[F, G, H, I, S, A] =
    new WriterStreamComonad[F, G, H, I, S, A](src.map {
      case (state, a) => WriterT[G, S, A]((state -> a).pure)
    })
}

case class WriterStreamNat[F[_], G[_], H[_], I[_], S, T](stream: F[WriterT[G, S, T]])(
  override implicit protected val nat: G ~> H
) extends WriterStream[F, G, H, I, S, T]
    with WriterStreamConcatNat[F, G, H, S, T]
    with WriterStreamTupleNat[F, G, H, S, T]
    with WithNat[G, H] {

  override type Return[SA, A] = WriterStreamNat[F, G, H, I, SA, A]

  override protected def apply[SA, A](src: F[WriterT[G, SA, A]]): WriterStreamNat[F, G, H, I, SA, A] =
    new WriterStreamNat[F, G, H, I, SA, A](src)
}

object WriterStreamNat {
  def apply[F[_]: Functor, G[_]: Applicative, H[_], I[_], S, A](src: F[A], initial: S)(
    implicit nat: G ~> H
  ): WriterStreamNat[F, G, H, I, S, A] =
    new WriterStreamNat[F, G, H, I, S, A](src.map(a => WriterT((initial, a).pure)))

  def apply[F[_]: Functor, G[_]: Applicative, H[_], I[_], S: Monoid, A](
    src: F[A]
  )(implicit nat: G ~> H): WriterStreamNat[F, G, H, I, S, A] =
    new WriterStreamNat[F, G, H, I, S, A](src.map(a => WriterT.liftF(a.pure)))

  def apply[F[_]: Functor, G[_]: Applicative, H[_], I[_], S, A](
    src: F[(S, A)]
  )(implicit nat: G ~> H): WriterStreamNat[F, G, H, I, S, A] =
    new WriterStreamNat[F, G, H, I, S, A](src.map {
      case (state, a) => WriterT((state -> a).pure)
    })

}
