package otters

import _root_.fs2.{Stream => Fs2Stream}
import cats.effect.IO
import cats.kernel.Monoid
import cats.{~>, Bimonad, Eval, Monad}
import org.scalatest.BeforeAndAfterAll
import otters.syntax.fs2.comonad._

class IndexedStateStreamComonadFs2Suite extends IndexedStateStreamFs2Suite[Eval] with BeforeAndAfterAll {

  override implicit def G: Monad[Eval] = implicitly[Bimonad[Eval]]
  override implicit def nat: ~>[Eval, IO] = new ~>[Eval, IO] {
    override def apply[A](fa: Eval[A]): IO[A] = IO.eval(fa)
  }

  override def mkWriterStream[S: Monoid, A](src: Fs2Stream[IO, A]): WriterStream[Fs2Stream[IO, ?], Eval, IO, IO, S, A] =
    src.toWriterStream[Eval, S]

  override def mkWriterStream[S, A](src: Fs2Stream[IO, (S, A)]): WriterStream[Fs2Stream[IO, ?], Eval, IO, IO, S, A] =
    src.toWriterStream[Eval]

  override def extract[A](fa: Eval[A]): A = fa.value

}
