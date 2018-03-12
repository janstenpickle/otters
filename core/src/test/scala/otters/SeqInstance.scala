package otters

import cats.instances.list._
import cats.{Apply, Functor, Id, Semigroupal}

import scala.collection.immutable
import scala.concurrent.duration.FiniteDuration

object SeqInstance extends TupleStream[Seq, Id, Id] {
  override def via[A, B](fa: Seq[A])(pipe: Pipe[Seq, A, B]): Seq[B] = pipe(fa)

  override def mapAsync[A, B](fa: Seq[A])(f: A => Id[B]): Seq[B] = fa.map(f)

  override def mapAsyncN[A, B](fa: Seq[A])(parallelism: Int)(f: A => Id[B]): Seq[B] = fa.map(f)

  override def to[A, B](fa: Seq[A])(sink: Sink[Seq, Id, A, B]): Id[B] = sink(fa)

  override def grouped[A](fa: Seq[A])(count: Int): Seq[Seq[A]] = fa.grouped(count).toSeq

  override def groupedWithin[A](fa: Seq[A])(count: Int, timespan: FiniteDuration): Seq[Seq[A]] = grouped(fa)(count)

  override def flatMap[A, B](fa: Seq[A])(f: A => Seq[B]): Seq[B] = fa.flatMap(f)

  override def mapConcat[A, B](fa: Seq[A])(f: A => immutable.Iterable[B]): Seq[B] = fa.flatMap(f)

  override def fromIterator[A](iter: => Iterator[A]): Seq[A] = iter.toSeq

  override def fromSeq[A](seq: Seq[A]): Seq[A] = seq

  override def pure[A](x: A): Seq[A] = Seq(x)

  override def ap[A, B](ff: Seq[A => B])(fa: Seq[A]): Seq[B] = implicitly[Apply[List]].ap(ff.toList)(fa.toList)

  override def zip[A, B](fa: Seq[A])(fb: Seq[B]): Seq[(A, B)] = fa.zip(fb)

  override implicit def H: Functor[Id] with Semigroupal[Id] = cats.catsInstancesForId
}
