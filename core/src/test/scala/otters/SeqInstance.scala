package otters

import cats.instances.list._
import cats.{Apply, Functor, Id, Semigroupal, StackSafeMonad}

import scala.collection.immutable
import scala.concurrent.duration.FiniteDuration

object SeqInstance extends EitherStreamFunctionPipeSink[Seq, Id, Id] with StackSafeMonad[Seq] {
  override def via[A, B](fa: Seq[A])(pipe: FunctionPipe[Seq, A, B]): Seq[B] = pipe(fa)

  override def mapAsync[A, B](fa: Seq[A])(f: A => Id[B]): Seq[B] = fa.map(f)

  override def mapAsyncN[A, B](fa: Seq[A])(parallelism: Int)(f: A => Id[B]): Seq[B] = fa.map(f)

  override def to[A, B](fa: Seq[A])(sink: FunctionSink[Seq, Id, A, B]): Id[B] = sink(fa)

  override def grouped[A](fa: Seq[A])(count: Int): Seq[Seq[A]] = fa.grouped(count).toSeq

  override def groupedWithin[A](fa: Seq[A])(count: Int, timespan: FiniteDuration): Seq[Seq[A]] = grouped(fa)(count)

  override def flatMap[A, B](fa: Seq[A])(f: A => Seq[B]): Seq[B] = fa.flatMap(f)

  override def mapConcat[A, B](fa: Seq[A])(f: A => immutable.Iterable[B]): Seq[B] = fa.flatMap(f)

  override def fromIterator[A](iter: => Iterator[A]): Seq[A] = iter.toSeq

  override def fromSeq[A](seq: Seq[A]): Seq[A] = seq

  override def pure[A](x: A): Seq[A] = Seq(x)

  override def ap[A, B](ff: Seq[A => B])(fa: Seq[A]): Seq[B] = implicitly[Apply[List]].ap(ff.toList)(fa.toList)

  override def zip[A, B](fa: Seq[A])(fb: Seq[B]): Seq[(A, B)] = fa.zip(fb)

  override def collect[A, B](fa: Seq[A])(pf: PartialFunction[A, B]): Seq[B] = fa.collect(pf)

  override def toEitherSinks[A, B, C, D, E](
    fab: Seq[Either[A, B]]
  )(lSink: FunctionSink[Seq, Id, A, C], rSink: FunctionSink[Seq, Id, B, D])(combine: (C, D) => E): Id[E] = {
    val l = lSink(fab.collect { case Left(a) => a })
    val r = rSink(fab.collect { case Right(b) => b })

    combine(l, r)
  }

  override def toSinks[A, B, C, D, E](
    fab: Seq[(A, B)]
  )(lSink: FunctionSink[Seq, Id, A, C], rSink: FunctionSink[Seq, Id, B, D])(combine: (C, D) => E): Id[E] = {
    val l = lSink(fab.map(_._1))
    val r = rSink(fab.map(_._2))

    combine(l, r)
  }

  override def fanOutFanIn[A, B, C, D](
    fab: Seq[(A, B)]
  )(lPipe: FunctionPipe[Seq, A, C], rPipe: FunctionPipe[Seq, B, D]): Seq[(C, D)] = {
    val l = lPipe(fab.map(_._1))
    val r = rPipe(fab.map(_._2))

    l.zip(r)
  }
}
