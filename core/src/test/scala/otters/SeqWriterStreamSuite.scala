package otters

import cats.Id
import cats.data.WriterT
import cats.kernel.Monoid

class SeqWriterStreamSuite
    extends WriterStreamSuite[Seq, Id, Id, FunctionPipe[Seq, ?, ?], FunctionSink[Seq, Id, ?, ?]]
    with SeqStreamBase {

  import WriterSyntax._

  override def mkWriterStream[S: Monoid, A](src: Seq[A]): WriterT[Seq, S, A] = src.toWriter

  override def mkWriterStream[S, A](src: Seq[A], initial: S): WriterT[Seq, S, A] = src.toWriter(initial)

  override def mkWriterStream[S, A](src: Seq[(S, A)]): WriterT[Seq, S, A] = src.toWriter
}
