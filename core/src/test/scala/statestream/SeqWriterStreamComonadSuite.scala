package statestream

import cats.kernel.Monoid
import cats.{~>, Bimonad, Eval, Id, Monad}
import org.scalatest.BeforeAndAfterAll

class SeqWriterStreamComonadSuite extends WriterStreamSuite[Seq, Eval, Id, Id] with BeforeAndAfterAll {

  override implicit def G: Monad[Eval] = implicitly[Bimonad[Eval]]
  override implicit def nat: ~>[Eval, Id] = new ~>[Eval, Id] {
    override def apply[A](fa: Eval[A]): Id[A] = fa.value
  }

  override implicit def F: TupleStream[Seq, Id, Id] = SeqInstance

  override implicit def H: Monad[Id] = cats.catsInstancesForId

  override def mkWriterStream[S: Monoid, A](src: Seq[A]): WriterStream[Seq, Eval, Id, Id, S, A] =
    WriterStreamComonad(src)

  override def mkWriterStream[S, A](src: Seq[(S, A)]): WriterStream[Seq, Eval, Id, Id, S, A] =
    WriterStreamComonad(src)

  override def mkPipe[A, B](f: A => B): Pipe[Seq, A, B] = _.map(f)

  override def mkSeqSink[A]: Sink[Seq, Id, A, Id[Seq[A]]] = identity

  override def extract[A](fa: Eval[A]): A = fa.value

  override def runStream[A](stream: Seq[A]): Seq[A] = stream

  override def materialize[A](i: Id[A]): A = i

  override def waitFor[A](fut: Id[A]): A = fut
}
