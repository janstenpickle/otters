package otters.instances.monix.tail

import cats.effect.Effect
import cats.{Functor, Semigroupal}
import monix.tail.Iterant
import otters.{Pipe, Sink, TupleStream}

import scala.collection.immutable
import scala.concurrent.duration.FiniteDuration

trait IterantInstances {
  implicit def iterantInstances[F[_]](implicit ev: Effect[F]): TupleStream[Iterant[F, ?], F, F] =
    new TupleStream[Iterant[F, ?], F, F] {
      override implicit def H: Functor[F] with Semigroupal[F] = ev

      override def map[A, B](fa: Iterant[F, A])(f: A => B): Iterant[F, B] = fa.map(f)

      override def mapAsync[A, B](fa: Iterant[F, A])(f: A => F[B]): Iterant[F, B] = fa.mapEval(f)

      override def mapAsyncN[A, B](fa: Iterant[F, A])(parallelism: Int)(f: A => F[B]): Iterant[F, B] = fa.mapEval(f)

      override def to[A, B](fa: Iterant[F, A])(sink: Sink[Iterant[F, ?], F, A, B]): F[B] = sink(fa)

      override def grouped[A](fa: Iterant[F, A])(count: Int): Iterant[F, Seq[A]] = fa.bufferTumbling(count)

      override def groupedWithin[A](fa: Iterant[F, A])(count: Int, timespan: FiniteDuration): Iterant[F, Seq[A]] =
        fa.bufferTumbling(count)

      override def flatMap[A, B](fa: Iterant[F, A])(f: A => Iterant[F, B]): Iterant[F, B] = fa.flatMap(f)

      override def mapConcat[A, B](fa: Iterant[F, A])(f: A => immutable.Iterable[B]): Iterant[F, B] =
        fa.flatMap(a => Iterant.fromIterable(f(a)))

      override def fromIterator[A](iter: => Iterator[A]): Iterant[F, A] = Iterant.fromIterator(iter)

      override def fromSeq[A](seq: Seq[A]): Iterant[F, A] = Iterant.fromSeq(seq)

      override def via[A, B](fa: Iterant[F, A])(pipe: Pipe[Iterant[F, ?], A, B]): Iterant[F, B] = pipe(fa)

      override def zip[A, B](fa: Iterant[F, A])(fb: Iterant[F, B]): Iterant[F, (A, B)] = fa.zip(fb)

      override def pure[A](x: A): Iterant[F, A] = Iterant.pure(x)

      override def ap[A, B](ff: Iterant[F, A => B])(fa: Iterant[F, A]): Iterant[F, B] = ff.flatMap(f => fa.map(f))
    }
}
