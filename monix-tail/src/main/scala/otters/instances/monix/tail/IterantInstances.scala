package otters.instances.monix.tail

import cats.effect.Effect
import cats.syntax.flatMap._
import cats.syntax.functor._
import monix.tail.Iterant
import otters._

import scala.collection.immutable
import scala.concurrent.duration.FiniteDuration

trait IterantInstances {
  implicit def iterantInstances[F[_]](implicit ev: Effect[F]): EitherStreamFunctionPipeSink[Iterant[F, ?], F, F] =
    new EitherStreamFunctionPipeSink[Iterant[F, ?], F, F] {
      override def map[A, B](fa: Iterant[F, A])(f: A => B): Iterant[F, B] = fa.map(f)

      override def mapAsync[A, B](fa: Iterant[F, A])(f: A => F[B]): Iterant[F, B] = fa.mapEval(f)

      override def mapAsyncN[A, B](fa: Iterant[F, A])(parallelism: Int)(f: A => F[B]): Iterant[F, B] = fa.mapEval(f)

      override def grouped[A](fa: Iterant[F, A])(count: Int): Iterant[F, Seq[A]] = fa.bufferTumbling(count)

      override def groupedWithin[A](fa: Iterant[F, A])(count: Int, timespan: FiniteDuration): Iterant[F, Seq[A]] =
        fa.bufferTumbling(count)

      override def flatMap[A, B](fa: Iterant[F, A])(f: A => Iterant[F, B]): Iterant[F, B] = fa.flatMap(f)

      override def mapConcat[A, B](fa: Iterant[F, A])(f: A => immutable.Iterable[B]): Iterant[F, B] =
        fa.flatMap(a => Iterant.fromIterable(f(a)))

      override def fromIterator[A](iter: => Iterator[A]): Iterant[F, A] = Iterant.fromIterator(iter)

      override def fromSeq[A](seq: Seq[A]): Iterant[F, A] = Iterant.fromSeq(seq)

      override def zip[A, B](fa: Iterant[F, A])(fb: Iterant[F, B]): Iterant[F, (A, B)] = fa.zip(fb)

      override def pure[A](x: A): Iterant[F, A] = Iterant.pure(x)

      override def ap[A, B](ff: Iterant[F, A => B])(fa: Iterant[F, A]): Iterant[F, B] = ff.flatMap(f => fa.map(f))

      override def collect[A, B](fa: Iterant[F, A])(pf: PartialFunction[A, B]): Iterant[F, B] = fa.collect(pf)

      override def tailRecM[A, B](a: A)(f: A => Iterant[F, Either[A, B]]): Iterant[F, B] = Iterant.tailRecM(a)(f)

      override def toEitherSinks[A, B, C, D, E](fab: Iterant[F, Either[A, B]])(
        lSink: FunctionSink[Iterant[F, ?], F, A, C],
        rSink: FunctionSink[Iterant[F, ?], F, B, D]
      )(combine: (C, D) => E): F[E] = {
        val l = lSink(fab.collect { case Left(a) => a })
        val r = rSink(fab.collect { case Right(b) => b })

        l.flatMap(ll => r.map(combine(ll, _)))
      }

      override def toSinks[A, B, C, D, E](fab: Iterant[F, (A, B)])(
        lSink: FunctionSink[Iterant[F, ?], F, A, C],
        rSink: FunctionSink[Iterant[F, ?], F, B, D]
      )(combine: (C, D) => E): F[E] = {
        val l = lSink(fab.map(_._1))
        val r = rSink(fab.map(_._2))

        l.flatMap(ll => r.map(combine(ll, _)))
      }

      override def fanOutFanIn[A, B, C, D](
        fab: Iterant[F, (A, B)]
      )(lPipe: FunctionPipe[Iterant[F, ?], A, C], rPipe: FunctionPipe[Iterant[F, ?], B, D]): Iterant[F, (C, D)] = {
        val l = lPipe(fab.map(_._1))
        val r = rPipe(fab.map(_._2))

        zip(l)(r)
      }
    }
}
