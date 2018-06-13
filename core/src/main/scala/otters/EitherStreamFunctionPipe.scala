package otters

import cats.syntax.functor._
import cats.syntax.flatMap._

import scala.collection.immutable
import scala.concurrent.duration.FiniteDuration

trait EitherStreamFunctionPipe[F[_], G[_], H[_], I]
    extends EitherStreamPipe[FunctionPipe[F, ?, ?], G, FunctionSink[F, H, I, ?], FunctionSink[F, H, ?, ?], I] {
  implicit val underlying: EitherStream[F, G, H, FunctionPipe[F, ?, ?], FunctionSink[F, H, ?, ?]]

  override def collect[A, B](fa: FunctionPipe[F, I, A])(pf: PartialFunction[A, B]): FunctionPipe[F, I, B] =
    (fi: F[I]) => underlying.collect(fa(fi))(pf)

  override def fromIterator[A](iter: => Iterator[A]): FunctionPipe[F, I, A] =
    (_: F[I]) => underlying.fromIterator(iter)

  override def fromSeq[A](seq: Seq[A]): FunctionPipe[F, I, A] = (_: F[I]) => underlying.fromSeq(seq)

  override def grouped[A](fa: FunctionPipe[F, I, A])(count: Int): FunctionPipe[F, I, Seq[A]] =
    (fi: F[I]) => underlying.grouped(fa(fi))(count)

  override def groupedWithin[A](
    fa: FunctionPipe[F, I, A]
  )(count: Int, timespan: FiniteDuration): FunctionPipe[F, I, Seq[A]] =
    (fi: F[I]) => underlying.groupedWithin(fa(fi))(count, timespan)

  override def map[A, B](fa: FunctionPipe[F, I, A])(f: A => B): FunctionPipe[F, I, B] =
    (fi: F[I]) => fa(fi).map(f)

  override def mapAsync[A, B](fa: FunctionPipe[F, I, A])(f: A => G[B]): FunctionPipe[F, I, B] =
    (fi: F[I]) => underlying.mapAsync(fa(fi))(f)

  override def mapAsyncN[A, B](fa: FunctionPipe[F, I, A])(parallelism: Int)(f: A => G[B]): FunctionPipe[F, I, B] =
    (fi: F[I]) => underlying.mapAsyncN(fa(fi))(parallelism)(f)

  override def mapConcat[A, B](fa: FunctionPipe[F, I, A])(f: A => immutable.Iterable[B]): FunctionPipe[F, I, B] =
    (fi: F[I]) => underlying.mapConcat(fa(fi))(f)

  override def flatMap[A, B](fa: FunctionPipe[F, I, A])(f: A => FunctionPipe[F, I, B]): FunctionPipe[F, I, B] =
    (fi: F[I]) => fa(fi).flatMap(f(_)(fi))

  override def pure[A](x: A): FunctionPipe[F, I, A] = (_: F[I]) => underlying.pure(x)

  override def tailRecM[A, B](a: A)(f: A => FunctionPipe[F, I, Either[A, B]]): FunctionPipe[F, I, B] =
    (fi: F[I]) => underlying.tailRecM(a)(f(_)(fi))

  override def tupleLeftVia[A, B, C](
    fab: FunctionPipe[F, I, (A, B)]
  )(lPipe: FunctionPipe[F, A, C]): FunctionPipe[F, I, (C, B)] =
    fanOutFanIn(fab)(lPipe, identity)

  override def tupleRightVia[A, B, C](fab: FunctionPipe[F, I, (A, B)])(
    rPipe: FunctionPipe[F, B, C]
  ): FunctionPipe[F, I, (A, C)] = fanOutFanIn(fab)(identity, rPipe)

  override def leftVia[A, B, C](fa: FunctionPipe[F, I, Either[A, B]])(
    lPipe: FunctionPipe[F, A, C]
  ): FunctionPipe[F, I, Either[C, B]] = via[A, B, C, B](fa)(lPipe, identity)

  override def rightVia[A, B, C](fa: FunctionPipe[F, I, Either[A, B]])(
    rPipe: FunctionPipe[F, B, C]
  ): FunctionPipe[F, I, Either[A, C]] = via[A, B, A, C](fa)(identity, rPipe)

  override def to[A, B](fa: FunctionPipe[F, I, A])(sink: FunctionSink[F, H, A, B]): FunctionSink[F, H, I, B] =
    fa.andThen(sink)

  override def via[A, B](fa: FunctionPipe[F, I, A])(pipe: FunctionPipe[F, A, B]): FunctionPipe[F, I, B] =
    fa.andThen(pipe)

  override def zip[A, B](fa: FunctionPipe[F, I, A])(fb: FunctionPipe[F, I, B]): FunctionPipe[F, I, (A, B)] =
    (fi: F[I]) => underlying.zip(fa(fi))(fb(fi))
}
