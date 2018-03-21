package otters.syntax.fs2

import _root_.fs2.Stream
import cats.effect.IO
import otters.syntax.{WriterTExtendedSyntax, WriterTSyntax}
import otters.{FunctionPipe, FunctionSink}

trait Fs2IOWriterTSyntax
    extends WriterTSyntax
    with WriterTExtendedSyntax[FunctionPipe[IO, ?, ?], FunctionSink[Stream[IO, ?], IO, ?, ?]]
