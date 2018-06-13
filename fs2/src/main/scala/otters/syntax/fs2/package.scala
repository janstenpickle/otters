package otters.syntax

import _root_.fs2.Stream
import cats.effect.IO

package object fs2 {
  type StreamIO[A] = Stream[IO, A]

  object either extends Fs2IOEitherTSyntax
  object writer extends Fs2IOWriterTSyntax
}
