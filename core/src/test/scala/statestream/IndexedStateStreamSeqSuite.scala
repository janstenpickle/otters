package statestream

import cats.{Id, Monad}

trait IndexedStateStreamSeqSuite[G[_]] extends IndexedStateStreamSuite[Seq, G, Id, Id] {

  override implicit def F: TupleStream[Seq, Id, Id] = SeqInstance

  override implicit def H: Monad[Id] = cats.catsInstancesForId

  override def mkPipe[A, B](f: A => B): Pipe[Seq, A, B] = _.map(f)

  override def mkSeqSink[A]: Sink[Seq, Id, A, Id[Seq[A]]] = identity

  override def runStream[A](stream: Seq[A]): Seq[A] = stream

  override def materialize[A](i: Id[A]): A = i

  override def waitFor[A](fut: Id[A]): A = fut
}
