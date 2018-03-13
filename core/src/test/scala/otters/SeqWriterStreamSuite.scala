package otters

import cats.Id
import cats.data.WriterT
import cats.kernel.Monoid
import otters.syntax.writer._

class SeqWriterStreamSuite extends WriterStreamSuite[Seq, Id, Id] with SeqStreamBase {
  override def mkWriterStream[S: Monoid, A](src: Seq[A]): WriterT[Seq, S, A] = src.toWriter

  override def mkWriterStream[S, A](src: Seq[A], initial: S): WriterT[Seq, S, A] = src.toWriter(initial)

  override def mkWriterStream[S, A](src: Seq[(S, A)]): WriterT[Seq, S, A] = src.toWriter
}
