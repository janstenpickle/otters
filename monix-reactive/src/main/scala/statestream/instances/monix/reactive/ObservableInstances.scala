package statestream.instances.monix.reactive

import _root_.monix.eval.Task
import _root_.monix.reactive.Observable
import cats.{Apply, Functor, Semigroupal}
import statestream.{Pipe, Sink, TupleStream}

import scala.collection.immutable
import scala.concurrent.duration.FiniteDuration

trait ObservableInstances {
  implicit def observableInstances(implicit ev: Apply[Task]): TupleStream[Observable, Task, Task] =
    new TupleStream[Observable, Task, Task] {
      override implicit def H: Functor[Task] with Semigroupal[Task] = ev

      override def map[A, B](fa: Observable[A])(f: A => B): Observable[B] = fa.map(f)

      override def mapAsync[A, B](fa: Observable[A])(f: A => Task[B]): Observable[B] = fa.mapTask(f)

      override def mapAsyncN[A, B](fa: Observable[A])(parallelism: Int)(f: A => Task[B]): Observable[B] =
        fa.mapParallelUnordered(1)(f)

      override def via[A, B](fa: Observable[A])(pipe: Pipe[Observable, A, B]): Observable[B] = pipe(fa)

      override def to[A, B](fa: Observable[A])(sink: Sink[Observable, Task, A, B]): Task[B] = sink(fa)

      override def grouped[A](fa: Observable[A])(count: Int): Observable[Seq[A]] = fa.bufferTumbling(count)

      override def groupedWithin[A](fa: Observable[A])(count: Int, timespan: FiniteDuration): Observable[Seq[A]] =
        fa.bufferTimedAndCounted(timespan, count)

      override def flatMap[A, B](fa: Observable[A])(f: A => Observable[B]): Observable[B] = fa.flatMap(f)

      override def mapConcat[A, B](fa: Observable[A])(f: A => immutable.Iterable[B]): Observable[B] =
        fa.concatMap(a => Observable(f(a).toSeq: _*))

      override def fromIterator[A](iter: => Iterator[A]): Observable[A] = Observable(iter.toSeq: _*)

      override def pure[A](x: A): Observable[A] = Observable.pure(x)

      override def ap[A, B](ff: Observable[A => B])(fa: Observable[A]): Observable[B] = ff.flatMap(fa.map)

      override def fromSeq[A](seq: Seq[A]): Observable[A] = Observable(seq: _*)

      override def zip[A, B](fa: Observable[A])(fb: Observable[B]): Observable[(A, B)] = fa.zip(fb)
    }
}
