package otters

import _root_.fs2.{Stream => Fs2Stream}
import cats.data.WriterT
import cats.effect.IO
import cats.kernel.Monoid

class Fs2PipeWriterStreamSuite
    extends Fs2PipeTestBase[Int]
    with WriterStreamSuite[FunctionPipe[Fs2Stream[IO, ?], Int, ?], IO, FunctionSink[Fs2Stream[IO, ?], IO, Int, ?], FunctionPipe[
      Fs2Stream[IO, ?],
      ?,
      ?
    ], FunctionSink[Fs2Stream[IO, ?], IO, ?, ?]] {

  import WriterSyntax._

  override def input: Fs2Stream[IO, Int] = Fs2Stream.emit(1)

  override def mkWriterStream[L: Monoid, A](
    src: FunctionPipe[fs2.Stream[IO, ?], Int, A]
  ): WriterT[FunctionPipe[fs2.Stream[IO, ?], Int, ?], L, A] = src.toWriter

  override def mkWriterStream[L, A](
    src: FunctionPipe[fs2.Stream[IO, ?], Int, A],
    initial: L
  ): WriterT[FunctionPipe[fs2.Stream[IO, ?], Int, ?], L, A] = src.toWriter(initial)

  override def mkWriterStream[L, A](
    src: FunctionPipe[fs2.Stream[IO, ?], Int, (L, A)]
  ): WriterT[FunctionPipe[fs2.Stream[IO, ?], Int, ?], L, A] = src.toWriter
}
