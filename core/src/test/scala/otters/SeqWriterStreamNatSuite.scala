package otters

import cats.kernel.Monoid
import cats.{~>, Bimonad, Eval, Id, Monad}

class SeqWriterStreamNatSuite extends SeqWriterStreamSuite[Eval] {
  override implicit def G: Monad[Eval] = implicitly[Bimonad[Eval]]
  override implicit def nat: ~>[Eval, Id] = new ~>[Eval, Id] {
    override def apply[A](fa: Eval[A]): Id[A] = fa.value
  }

  override def mkWriterStream[S: Monoid, A](src: Seq[A]): WriterStream[Seq, Eval, Id, Id, S, A] =
    WriterStreamNat(src)

  override def mkWriterStream[S, A](src: Seq[(S, A)]): WriterStream[Seq, Eval, Id, Id, S, A] =
    WriterStreamNat(src)

  override def extract[A](fa: Eval[A]): A = fa.value

}
