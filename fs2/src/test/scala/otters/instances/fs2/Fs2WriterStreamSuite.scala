package otters

import _root_.fs2.{Stream => Fs2Stream}
import cats.data.WriterT
import cats.effect.IO
import cats.kernel.Monoid
import otters.syntax.writer._

class Fs2WriterStreamSuite extends Fs2TestBase with WriterStreamSuite[Fs2Stream[IO, ?], IO, IO] {
  override def mkWriterStream[S: Monoid, A](src: Fs2Stream[IO, A]): WriterT[fs2.Stream[IO, ?], S, A] = src.toWriter

  override def mkWriterStream[S, A](src: Fs2Stream[IO, A], initial: S): WriterT[fs2.Stream[IO, ?], S, A] =
    src.toWriter(initial)

  override def mkWriterStream[S, A](src: Fs2Stream[IO, (S, A)]): WriterT[fs2.Stream[IO, ?], S, A] = src.toWriter
}
