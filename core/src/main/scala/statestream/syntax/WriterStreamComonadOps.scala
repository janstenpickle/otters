package statestream.syntax

import cats.{Applicative, Comonad, Functor}
import cats.kernel.Monoid
import statestream.WriterStreamComonad

trait WriterStreamComonadOps { self: WriterStreamOps =>
  implicit class ToWriterStreamComonad[A](stream: Stream[A])(implicit ev: Functor[Stream]) {
    def toWriterStream[S: Monoid]: WriterStrm[S, A] =
      WriterStreamComonad(stream)

    def toWriterStream[F[_]: Applicative: Comonad, S: Monoid]: WriterStreamT[F, S, A] =
      WriterStreamComonad(stream)

    def toWriterStream[S](initial: S): WriterStrm[S, A] =
      WriterStreamComonad(stream, initial)

    def toWriterStream[F[_]: Applicative: Comonad, S](initial: S): WriterStreamT[F, S, A] =
      WriterStreamComonad(stream, initial)
  }

  implicit class ToWriterStreamTupleComonad[S, A](stream: Stream[(S, A)])(implicit ev: Functor[Stream]) {
    def toWriterStream: WriterStrm[S, A] =
      WriterStreamComonad(stream)

    def toWriterStream[F[_]: Applicative: Comonad]: WriterStreamT[F, S, A] =
      WriterStreamComonad(stream)
  }
}
