package statestream

import cats.data.{IndexedStateT, StateT}
import cats.syntax.applicative._
import cats.syntax.functor._
import cats.{~>, Always, Applicative, Comonad, Eval, FlatMap, Functor, Monad, Monoid, Traverse}
import statestream.syntax.stream._

import scala.collection.immutable
import scala.concurrent.duration.FiniteDuration

trait IndexedStateStreamBase[F[_], G[_], SA, SB, T] {
  type Return[SC, SD, A]

  val stream: F[IndexedStateT[G, SA, SB, T]]

  protected def apply[SC, SD, A](run: F[IndexedStateT[G, SC, SD, A]]): Return[SC, SD, A]

  def map[C](f: T => C)(implicit F: Functor[F], G: Functor[G]): Return[SA, SB, C] = apply(stream.map(_.map(f)))

  def flatMapS[A](f: T => StateT[G, SB, A])(implicit F: Functor[F], G: FlatMap[G]): Return[SA, SB, A] =
    apply(stream.map(_.flatMap(f)))

  def transform[A, SC](f: (SB, T) => (SC, A))(implicit F: Functor[F], G: Functor[G]): Return[SA, SC, A] =
    apply(stream.map(_.transform(f)))

  def transformF[A, SC](f: G[(SB, T)] => G[(SC, A)])(implicit F: Functor[F], G: Monad[G]): Return[SA, SC, A] =
    apply(stream.map(_.transformF(f)))

  def modify[SC](f: SB => SC)(implicit F: Functor[F], G: Functor[G]): Return[SA, SC, T] =
    apply(stream.map(_.modify(f)))
}

trait WithComonad[G[_]] {
  protected def CG: Comonad[G]
}

trait WithNat[G[_], H[_]] {
  protected def nat: G ~> H
}

trait IndexedStateTupleBase[F[_], G[_], H[_], SA, SB, T] {
  def toTuple(implicit F: AsyncStream[F, H], G: FlatMap[G], SA: Monoid[SA], SB: Monoid[SB]): F[(SB, T)]
  def toTuple(initial: SA)(implicit F: AsyncStream[F, H], G: FlatMap[G]): F[(SB, T)]
}

trait IndexedFlowStateTupleComonad[F[_], G[_], H[_], SA, SB, T] {
  self: IndexedStateTupleBase[F, G, H, SA, SB, T] with WithComonad[G] with IndexedStateStreamBase[F, G, SA, SB, T] =>

  def toTuple(implicit F: AsyncStream[F, H], G: FlatMap[G], SA: Monoid[SA], SB: Monoid[SB]): F[(SB, T)] =
    stream.map(d => CG.extract(d.runEmpty))

  def toTuple(initial: SA)(implicit F: AsyncStream[F, H], G: FlatMap[G]): F[(SB, T)] =
    stream.map(d => CG.extract(d.run(initial)))
}

trait IndexedStateStreamTupleNat[F[_], G[_], H[_], SA, SB, T] {
  self: IndexedStateTupleBase[F, G, H, SA, SB, T] with WithNat[G, H] with IndexedStateStreamBase[F, G, SA, SB, T] =>

  def toTuple(implicit F: AsyncStream[F, H], G: FlatMap[G], SA: Monoid[SA], SB: Monoid[SB]): F[(SB, T)] =
    F.mapAsync(stream)(d => nat(d.runEmpty))

  def toTuple(initial: SA)(implicit F: AsyncStream[F, H], G: FlatMap[G]): F[(SB, T)] =
    F.mapAsync(stream)(d => nat(d.run(initial)))
}

trait IndexedStateStreamStreamOps[F[_], G[_], H[_], I[_], SA, SB, T] {
  self: IndexedStateStreamBase[F, G, SA, SB, T] with IndexedStateTupleBase[F, G, H, SA, SB, T] =>

  def via[SC, B](
    statePipe: Pipe[F, SB, SC],
    dataPipe: Pipe[F, T, B]
  )(implicit F: TupleStream[F, H, I], G: Monad[G], SA: Monoid[SA], SB: Monoid[SB], SC: Monoid[SC]): Return[SC, SC, B] =
    apply(F.fanOutFanIn(toTuple)(statePipe, dataPipe).map {
      case (state, data) =>
        StateT[G, SC, B](s => (SC.combine(s, state), data).pure[G])
    })

  def stateVia[SC](
    statePipe: Pipe[F, SB, SC]
  )(implicit F: TupleStream[F, H, I], G: Monad[G], SA: Monoid[SA], SB: Monoid[SB], SC: Monoid[SC]): Return[SC, SC, T] =
    apply(F.leftVia(toTuple)(statePipe).map {
      case (state, data) =>
        StateT[G, SC, T](s => (SC.combine(s, state), data).pure[G])
    })

  def dataVia[B](
    dataPipe: Pipe[F, T, B]
  )(implicit F: TupleStream[F, H, I], G: Monad[G], SA: Monoid[SA], SB: Monoid[SB]): Return[SB, SB, B] =
    apply(F.rightVia(toTuple)(dataPipe).map {
      case (state, data) =>
        StateT[G, SB, B](s => (SB.combine(s, state), data).pure[G])
    })

  def toSinks[A, B, C](stateSink: Sink[F, I, SB, A], dataSink: Sink[F, I, T, B])(
    combine: (A, B) => C,
  )(implicit F: TupleStream[F, H, I], G: FlatMap[G], SA: Monoid[SA], SB: Monoid[SB]): I[C] =
    F.toSinks[SB, T, A, B, C](toTuple)(stateSink, dataSink)(combine)

  def toSinks[A, B](
    stateSink: Sink[F, I, SB, A],
    dataSink: Sink[F, I, T, B]
  )(implicit F: TupleStream[F, H, I], G: FlatMap[G], SA: Monoid[SA], SB: Monoid[SB]): I[(A, B)] =
    toSinks[A, B, (A, B)](stateSink, dataSink)(_ -> _)

  def toSinks[A, B, C](initial: SA)(stateSink: Sink[F, I, SB, A], dataSink: Sink[F, I, T, B])(
    combine: (A, B) => C
  )(implicit F: TupleStream[F, H, I], G: FlatMap[G]): I[C] =
    F.toSinks[SB, T, A, B, C](toTuple(initial))(stateSink, dataSink)(combine)

  def toSinks[A, B](initial: SA)(
    stateSink: Sink[F, I, SB, A],
    dataSink: Sink[F, I, T, B]
  )(implicit F: TupleStream[F, H, I], G: FlatMap[G]): I[(A, B)] =
    toSinks[A, B, (A, B)](initial)(stateSink, dataSink)(_ -> _)
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

trait IndexedStateStreamGrouped[F[_], G[_], SA, SB, T] extends SeqInstances {
  self: IndexedStateStreamBase[F, G, SA, SB, T] =>

  def groupedWithin(
    n: Int,
    d: FiniteDuration
  )(implicit F: Stream[F], G: Applicative[IndexedStateT[G, SA, SB, ?]]): Return[SA, SB, Seq[T]] =
    apply(stream.groupedWithin(n, d).map(seqTraverse.sequence(_)))

  def group(n: Int)(implicit F: Stream[F], G: Applicative[IndexedStateT[G, SA, SB, ?]]): Return[SA, SB, Seq[T]] =
    apply(stream.grouped(n).map(seqTraverse.sequence(_)))
}

trait IndexedStateStreamAsync[F[_], G[_], H[_], SA, SB, T] { self: IndexedStateStreamBase[F, G, SA, SB, T] =>
  def mapAsync[C](parallelism: Int)(f: T => H[C])(
    implicit F: AsyncStream[F, H],
    G: Monad[G],
    H: FlatMap[H],
    SA: Monoid[SA],
    SB: Monoid[SB],
    nat: G ~> H
  ): Return[SB, SB, C] =
    asyncTransform(parallelism)(_.flatMapF(f))

  def flatMapAsync[SC, C](parallelism: Int)(f: T => IndexedStateT[H, SB, SC, C])(
    implicit F: AsyncStream[F, H],
    G: Monad[G],
    H: FlatMap[H],
    nat: G ~> H,
    SA: Monoid[SA],
    SC: Monoid[SC]
  ): Return[SC, SC, C] =
    asyncTransform(parallelism)(_.flatMap(f))

  private def asyncTransform[SC, C](parallelism: Int)(f: IndexedStateT[H, SA, SB, T] => IndexedStateT[H, SA, SC, C])(
    implicit F: AsyncStream[F, H],
    G: Applicative[G],
    H: FlatMap[H],
    SA: Monoid[SA],
    SC: Monoid[SC],
    nat: G ~> H
  ): Return[SC, SC, C] =
    apply(
      F.mapAsyncN(stream)(parallelism)(
        x =>
          f(x.mapK[H](nat)).runEmpty.map {
            case (s, b) => IndexedStateT.apply[G, SC, SC, C]((ss: SC) => G.pure(SC.combine(ss, s) -> b))
        }
      )
    )
}

trait IndexedFlowStateConcatOps {
  protected def makeSafe[C](cs: immutable.Iterable[C]): immutable.Iterable[Option[C]] =
    if (cs.isEmpty) Vector(None)
    else cs.map(Some(_))

  def head[F[_]: Applicative, S: Monoid, A]: ((S, immutable.Iterable[A])) => immutable.Iterable[StateT[F, S, A]] = {
    case (state, data) =>
      data match {
        case d: immutable.Seq[A] => headSeq[F, S, A](state, d)
        case d => headSeq[F, S, A](state, d.toVector)
      }
  }

  def tail[F[_]: Applicative, S: Monoid, A]: ((S, immutable.Iterable[A])) => immutable.Iterable[StateT[F, S, A]] = {
    case (state, data) =>
      data match {
        case d: immutable.Seq[A] => tailSeq[F, S, A](state, d)
        case d => tailSeq[F, S, A](state, d.toVector)
      }
  }

  def all[F[_]: Applicative, S: Monoid, A]: ((S, immutable.Iterable[A])) => immutable.Iterable[StateT[F, S, A]] = {
    case (state, data) =>
      data match {
        case immutable.Seq() => immutable.Seq.empty
        case d: immutable.Seq[A] => allSeq[F, S, A](state, d)
        case d => allSeq[F, S, A](state, d.toVector)
      }
  }

  def headSeq[F[_]: Applicative, S, A](state: S, as: immutable.Seq[A])(
    implicit S: Monoid[S]
  ): immutable.Seq[StateT[F, S, A]] =
    as match {
      case immutable.Seq() => immutable.Seq.empty
      case head +: tail =>
        StateT[F, S, A](s => (S.combine(s, state), head).pure) +: tail.map(a => StateT[F, S, A](s => (s, a).pure))
    }

  def tailSeq[F[_]: Applicative, S, A](state: S, as: immutable.Seq[A])(
    implicit S: Monoid[S]
  ): immutable.Seq[StateT[F, S, A]] =
    as match {
      case immutable.Seq() => immutable.Seq.empty
      case xs :+ last =>
        xs.map(a => StateT[F, S, A](s => (s, a).pure)) :+ StateT[F, S, A](s => (S.combine(s, state), last).pure)
    }

  def allSeq[F[_]: Applicative, S, A](state: S, as: immutable.Seq[A])(
    implicit S: Monoid[S]
  ): immutable.Seq[StateT[F, S, A]] = as.map(a => StateT[F, S, A](s => (S.combine(s, state), a).pure))
}

trait IndexedStateStreamConcatBase[F[_], G[_], H[_], SA, SB, T] extends IndexedFlowStateConcatOps {
  self: IndexedStateStreamBase[F, G, SA, SB, T] =>

  def mapConcat[C](
    f: T => immutable.Iterable[C],
    f2: ((SB, immutable.Iterable[C])) => immutable.Iterable[StateT[G, SB, C]]
  )(implicit F: AsyncStream[F, H], G: Monad[G], SA: Monoid[SA], SB: Monoid[SB]): Return[SB, SB, C]

  def safeMapConcat[C](
    f: T => immutable.Iterable[C],
    f2: ((SB, immutable.Iterable[Option[C]])) => immutable.Iterable[StateT[G, SB, Option[C]]]
  )(implicit F: AsyncStream[F, H], G: Monad[G], SA: Monoid[SA], SB: Monoid[SB]): Return[SB, SB, Option[C]]

  def mapConcatHead[C](
    f: T => immutable.Iterable[C]
  )(implicit F: AsyncStream[F, H], G: Monad[G], SA: Monoid[SA], SB: Monoid[SB]): Return[SB, SB, C] =
    mapConcat[C](f, head[G, SB, C])

  def safeMapConcatHead[C](
    f: T => immutable.Iterable[C],
  )(implicit F: AsyncStream[F, H], G: Monad[G], SA: Monoid[SA], SB: Monoid[SB]): Return[SB, SB, Option[C]] =
    safeMapConcat[C](f, head[G, SB, Option[C]])

  def mapConcatTail[C](
    f: T => immutable.Iterable[C]
  )(implicit F: AsyncStream[F, H], G: Monad[G], SA: Monoid[SA], SB: Monoid[SB]): Return[SB, SB, C] =
    mapConcat[C](f, tail[G, SB, C])

  def safeMapConcatTail[C](
    f: T => immutable.Iterable[C],
  )(implicit F: AsyncStream[F, H], G: Monad[G], SA: Monoid[SA], SB: Monoid[SB]): Return[SB, SB, Option[C]] =
    safeMapConcat[C](f, tail[G, SB, Option[C]])

  def mapConcatAll[C](
    f: T => immutable.Iterable[C]
  )(implicit F: AsyncStream[F, H], G: Monad[G], SA: Monoid[SA], SB: Monoid[SB]): Return[SB, SB, C] =
    mapConcat[C](f, all[G, SB, C])

  def safeMapConcatAll[C](
    f: T => immutable.Iterable[C],
  )(implicit F: AsyncStream[F, H], G: Monad[G], SA: Monoid[SA], SB: Monoid[SB]): Return[SB, SB, Option[C]] =
    safeMapConcat[C](f, all[G, SB, Option[C]])
}

trait IndexedFlowStateConcatComonad[F[_], G[_], H[_], SA, SB, T]
    extends IndexedStateStreamConcatBase[F, G, H, SA, SB, T] {
  self: IndexedStateStreamBase[F, G, SA, SB, T] with WithComonad[G] =>

  override def mapConcat[C](
    f: T => immutable.Iterable[C],
    f2: ((SB, immutable.Iterable[C])) => immutable.Iterable[StateT[G, SB, C]]
  )(implicit F: AsyncStream[F, H], G: Monad[G], SA: Monoid[SA], SB: Monoid[SB]): Return[SB, SB, C] =
    apply(stream.mapConcat(x => CG.extract(x.map(f).runEmpty.map[immutable.Iterable[StateT[G, SB, C]]](f2))))

  override def safeMapConcat[C](
    f: T => immutable.Iterable[C],
    f2: ((SB, immutable.Iterable[Option[C]])) => immutable.Iterable[StateT[G, SB, Option[C]]]
  )(implicit F: AsyncStream[F, H], G: Monad[G], SA: Monoid[SA], SB: Monoid[SB]): Return[SB, SB, Option[C]] =
    apply(
      stream.mapConcat(
        x =>
          CG.extract(
            x.map(b => makeSafe(f(b)))
              .runEmpty
              .map[immutable.Iterable[StateT[G, SB, Option[C]]]](f2)
        )
      )
    )
}

trait IndexedStateStreamConcatNat[F[_], G[_], H[_], SA, SB, T]
    extends IndexedStateStreamConcatBase[F, G, H, SA, SB, T] {
  self: IndexedStateStreamBase[F, G, SA, SB, T] with WithNat[G, H] =>

  def mapConcat[B](
    f: T => immutable.Iterable[B],
    f2: ((SB, immutable.Iterable[B])) => immutable.Iterable[StateT[G, SB, B]]
  )(implicit F: AsyncStream[F, H], G: Monad[G], SA: Monoid[SA], SB: Monoid[SB]): Return[SB, SB, B] =
    apply(
      F.mapAsync(stream)(x => nat(x.map(f).runEmpty.map[immutable.Iterable[StateT[G, SB, B]]](f2)))
        .mapConcat(identity)
    )

  def safeMapConcat[B](
    f: T => immutable.Iterable[B],
    f2: ((SB, immutable.Iterable[Option[B]])) => immutable.Iterable[StateT[G, SB, Option[B]]]
  )(implicit F: AsyncStream[F, H], G: Monad[G], SA: Monoid[SA], SB: Monoid[SB]): Return[SB, SB, Option[B]] =
    apply(
      F.mapAsync(stream)(
          x => nat(x.map(b => makeSafe(f(b))).runEmpty.map[immutable.Iterable[StateT[G, SB, Option[B]]]](f2))
        )
        .mapConcat(identity)
    )
}

trait IndexedStateStream[F[_], G[_], H[_], I[_], SA, SB, T]
    extends IndexedStateStreamBase[F, G, SA, SB, T]
    with IndexedStateStreamAsync[F, G, H, SA, SB, T]
    with IndexedStateStreamConcatBase[F, G, H, SA, SB, T]
    with IndexedStateTupleBase[F, G, H, SA, SB, T]
    with IndexedStateStreamStreamOps[F, G, H, I, SA, SB, T]
    with IndexedStateStreamGrouped[F, G, SA, SB, T] {
  override type Return[SC, SD, B] <: IndexedStateStream[F, G, H, I, SC, SD, B]
}

case class IndexedStateStreamComonad[F[_], G[_], H[_], I[_], SA, SB, T](stream: F[IndexedStateT[G, SA, SB, T]])(
  override implicit protected val CG: Comonad[G]
) extends IndexedStateStream[F, G, H, I, SA, SB, T]
    with IndexedFlowStateConcatComonad[F, G, H, SA, SB, T]
    with IndexedFlowStateTupleComonad[F, G, H, SA, SB, T]
    with WithComonad[G] {

  override type Return[SC, SD, A] = IndexedStateStreamComonad[F, G, H, I, SC, SD, A]

  override protected def apply[SC, SD, A](
    src: F[IndexedStateT[G, SC, SD, A]]
  ): IndexedStateStreamComonad[F, G, H, I, SC, SD, A] =
    new IndexedStateStreamComonad[F, G, H, I, SC, SD, A](src)
}

object IndexedStateStreamComonad {
  def apply[F[_]: Functor, G[_]: Applicative: Comonad, H[_], I[_], S, A](
    src: F[A]
  ): IndexedStateStreamComonad[F, G, H, I, S, S, A] =
    new IndexedStateStreamComonad[F, G, H, I, S, S, A](src.map(a => StateT[G, S, A](s => (s, a).pure)))

  def apply[F[_]: Functor, G[_]: Applicative: Comonad, H[_], I[_], S, A](
    src: F[(S, A)]
  )(implicit S: Monoid[S]): IndexedStateStreamComonad[F, G, H, I, S, S, A] =
    new IndexedStateStreamComonad[F, G, H, I, S, S, A](src.map {
      case (state, a) => StateT[G, S, A](s => (S.combine(s, state), a).pure)
    })
}

case class IndexedStateStreamNat[F[_], G[_], H[_], I[_], SA, SB, T](stream: F[IndexedStateT[G, SA, SB, T]])(
  override implicit protected val nat: G ~> H
) extends IndexedStateStream[F, G, H, I, SA, SB, T]
    with IndexedStateStreamConcatNat[F, G, H, SA, SB, T]
    with IndexedStateStreamTupleNat[F, G, H, SA, SB, T]
    with WithNat[G, H] {

  override type Return[SC, SD, A] = IndexedStateStreamNat[F, G, H, I, SC, SD, A]

  override protected def apply[SC, SD, A](
    src: F[IndexedStateT[G, SC, SD, A]]
  ): IndexedStateStreamNat[F, G, H, I, SC, SD, A] =
    new IndexedStateStreamNat[F, G, H, I, SC, SD, A](src)
}

object IndexedStateStreamNat {
  def apply[F[_]: Functor, G[_]: Applicative, H[_], I[_], S, A](
    src: F[A]
  )(implicit nat: G ~> H): IndexedStateStreamNat[F, G, H, I, S, S, A] =
    new IndexedStateStreamNat[F, G, H, I, S, S, A](src.map(a => StateT[G, S, A](s => (s, a).pure)))

  def apply[F[_]: Functor, G[_]: Applicative, H[_], I[_], S, A](
    src: F[(S, A)]
  )(implicit S: Monoid[S], nat: G ~> H): IndexedStateStreamNat[F, G, H, I, S, S, A] =
    new IndexedStateStreamNat[F, G, H, I, S, S, A](src.map {
      case (state, a) => StateT[G, S, A](s => (S.combine(s, state), a).pure)
    })

}
