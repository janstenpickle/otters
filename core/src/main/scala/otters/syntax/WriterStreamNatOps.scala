package otters.syntax

import cats.kernel.Monoid
import cats.{~>, Applicative, Functor, Id}
import otters.WriterStreamNat

trait WriterStreamNatOps { self: WriterStreamOps =>
  implicit class ToWriterStreamNat[A](stream: Stream[A])(implicit ev: Functor[Stream]) {
    def toWriterStream[S: Monoid](implicit nat: Id ~> Async): WriterStrm[S, A] =
      WriterStreamNat(stream)

    def toWriterStream[F[_]: Applicative, S: Monoid](implicit nat: F ~> Async): WriterStreamT[F, S, A] =
      WriterStreamNat(stream)

    def toWriterStream[S](initial: S)(implicit nat: Id ~> Async): WriterStrm[S, A] =
      WriterStreamNat(stream, initial)

    def toWriterStream[F[_]: Applicative, S](initial: S)(implicit nat: F ~> Async): WriterStreamT[F, S, A] =
      WriterStreamNat(stream, initial)

  }

  implicit class ToWriterStreamTupleNat[S, A](stream: Stream[(S, A)])(implicit ev: Functor[Stream]) {
    def toWriterStream(implicit nat: Id ~> Async): WriterStrm[S, A] =
      WriterStreamNat(stream)

    def toWriterStream[F[_]: Applicative](implicit nat: F ~> Async): WriterStreamT[F, S, A] =
      WriterStreamNat(stream)
  }
}
