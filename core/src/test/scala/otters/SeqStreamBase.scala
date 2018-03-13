package otters

import cats.{Id, Monad}

trait SeqStreamBase extends TestBase[Seq, Id, Id] {
  override implicit def F: EitherStream[Seq, Id, Id] = SeqInstance

  override def mkPipe[A, B](f: A => B): Pipe[Seq, A, B] = _.map(f)

  override def mkSeqSink[A]: Sink[Seq, Id, A, Id[Seq[A]]] = identity

  override def runStream[A](stream: Seq[A]): Seq[A] = stream

  override def materialize[A](i: Id[A]): A = i

  override def waitFor[A](fut: Id[A]): A = fut

  override implicit def G: Monad[Id] = cats.catsInstancesForId
}
