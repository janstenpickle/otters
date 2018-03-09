package statestream

import cats.effect.{Effect, IO}
import cats.kernel.Monoid
import cats.{~>, Bimonad, Eval, Monad}
import org.scalatest.BeforeAndAfterAll

import _root_.fs2.{Stream => Fs2Stream}

class IndexedStateStreamComonadFs2Suite extends IndexedStateStreamFs2Suite[Eval] with BeforeAndAfterAll {

  override implicit def G: Monad[Eval] = implicitly[Bimonad[Eval]]
  override implicit def nat: ~>[Eval, IO] = new ~>[Eval, IO] {
    override def apply[A](fa: Eval[A]): IO[A] = IO.eval(fa)
  }

  override def mkStateStream[S, A](src: Fs2Stream[IO, A]): IndexedStateStream[Fs2Stream[IO, ?], Eval, IO, IO, S, S, A] =
    IndexedStateStreamComonad[Fs2Stream[IO, ?], Eval, IO, IO, S, A](src)

  override def mkStateStream[S: Monoid, A](
    src: Fs2Stream[IO, (S, A)]
  ): IndexedStateStream[Fs2Stream[IO, ?], Eval, IO, IO, S, S, A] =
    IndexedStateStreamComonad[Fs2Stream[IO, ?], Eval, IO, IO, S, A](src)

  override def extract[A](fa: Eval[A]): A = fa.value

}
