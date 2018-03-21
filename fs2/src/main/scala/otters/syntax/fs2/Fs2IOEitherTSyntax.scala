package otters.syntax.fs2

import _root_.fs2.Stream
import cats.effect.IO
import otters.syntax.{EitherTExtendedSyntax, EitherTSyntax}
import otters.{FunctionPipe, FunctionSink}

trait Fs2IOEitherTSyntax
    extends EitherTSyntax
    with EitherTExtendedSyntax[FunctionPipe[IO, ?, ?], FunctionSink[Stream[IO, ?], IO, ?, ?]]
