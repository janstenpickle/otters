package otters.instances.monix.reactive

import _root_.monix.eval.Task
import _root_.monix.reactive.Observable
import monix.execution.Scheduler
import monix.reactive.observables.ConnectableObservable
import monix.reactive.subjects.{ConcurrentSubject, ReplaySubject}
import otters._
import otters.instances.monix.reactive

import scala.collection.immutable
import scala.concurrent.duration.FiniteDuration

trait ObservableInstances {
  implicit def observableInstances(implicit s: Scheduler): EitherStreamFunctionPipeSink[Observable, Task, Task] =
    new EitherStreamFunctionPipeSink[Observable, Task, Task] {
      override def map[A, B](fa: Observable[A])(f: A => B): Observable[B] = fa.map(f)

      override def mapAsync[A, B](fa: Observable[A])(f: A => Task[B]): Observable[B] = fa.mapTask(f)

      override def mapAsyncN[A, B](fa: Observable[A])(parallelism: Int)(f: A => Task[B]): Observable[B] =
        fa.mapParallelUnordered(1)(f)

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

      override def collect[A, B](fa: Observable[A])(pf: PartialFunction[A, B]): Observable[B] = fa.collect(pf)

      override def tailRecM[A, B](a: A)(f: A => Observable[Either[A, B]]): Observable[B] = Observable.tailRecM(a)(f)

      override def toEitherSinks[A, B, C, D, E](
        fab: Observable[Either[A, B]]
      )(lSink: Observable[A] => Task[C], rSink: Observable[B] => Task[D])(combine: (C, D) => E): Task[E] = {
        val subject = ReplaySubject[Either[A, B]]()

        ConnectableObservable.unsafeMulticast(fab, subject).connect()

        val l = lSink(subject.collect { case Left(a) => a })
        val r = rSink(subject.collect { case Right(b) => b })

        l.flatMap(ll => r.map(combine(ll, _)))
      }

      override def toSinks[A, B, C, D, E](
        fab: Observable[(A, B)]
      )(lSink: Observable[A] => Task[C], rSink: Observable[B] => Task[D])(combine: (C, D) => E): Task[E] = {
        val subject = ReplaySubject[(A, B)]()

        ConnectableObservable.unsafeMulticast(fab, subject).connect()

        val l = lSink(subject.map(_._1))
        val r = rSink(subject.map(_._2))

        l.flatMap(ll => r.map(combine(ll, _)))
      }

      override def fanOutFanIn[A, B, C, D](
        fab: Observable[(A, B)]
      )(lPipe: reactive.Pipe[A, C], rPipe: reactive.Pipe[B, D]): Observable[(C, D)] = {
        val subject = ConcurrentSubject.replay[(A, B)]

        ConnectableObservable.unsafeMulticast(fab, subject).connect()

        zip(lPipe(subject.map(_._1)))(rPipe(subject.map(_._2)))
      }

    }

  implicit def observablePipeInstances[I](
    implicit s: Scheduler,
    ev: EitherStreamFunctionPipeSink[Observable, Task, Task]
  ): EitherStreamFunctionPipe[Observable, Task, Task, I] =
    new EitherStreamFunctionPipe[Observable, Task, Task, I] {
      override implicit val underlying: EitherStream[
        Observable,
        Task,
        Task,
        FunctionPipe[Observable, ?, ?],
        FunctionSink[Observable, Task, ?, ?]
      ] = ev

      override def toEitherSinks[A, B, C, D, E](fab: FunctionPipe[Observable, I, Either[A, B]])(
        lSink: FunctionSink[Observable, Task, A, C],
        rSink: FunctionSink[Observable, Task, B, D]
      )(combine: (C, D) => E): FunctionSink[Observable, Task, I, E] = fab.andThen { x =>
        val subject = ReplaySubject[Either[A, B]]()

        ConnectableObservable.unsafeMulticast(x, subject).connect()

        val l = lSink(subject.collect { case Left(a) => a })
        val r = rSink(subject.collect { case Right(b) => b })

        l.flatMap(ll => r.map(combine(ll, _)))
      }

      override def toSinks[A, B, C, D, E](fab: FunctionPipe[Observable, I, (A, B)])(
        lSink: FunctionSink[Observable, Task, A, C],
        rSink: FunctionSink[Observable, Task, B, D]
      )(combine: (C, D) => E): FunctionSink[Observable, Task, I, E] = fab.andThen { x =>
        val subject = ReplaySubject[(A, B)]()

        ConnectableObservable.unsafeMulticast(x, subject).connect()

        val l = lSink(subject.map(_._1))
        val r = rSink(subject.map(_._2))

        l.flatMap(ll => r.map(combine(ll, _)))
      }

      override def fanOutFanIn[A, B, C, D](fab: FunctionPipe[Observable, I, (A, B)])(
        lPipe: FunctionPipe[Observable, A, C],
        rPipe: FunctionPipe[Observable, B, D]
      ): FunctionPipe[Observable, I, (C, D)] = fab.andThen { x =>
        val subject = ConcurrentSubject.replay[(A, B)]

        ConnectableObservable.unsafeMulticast(x, subject).connect()

        observableInstances.zip(lPipe(subject.map(_._1)))(rPipe(subject.map(_._2)))
      }
    }
}
