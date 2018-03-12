package otters.syntax.fs2

import cats.effect.IO
import otters.syntax.WriterStreamOps

trait Fs2Syntax extends WriterStreamOps {
  override type Stream[A] = _root_.fs2.Stream[IO, A]
  override type Async[A] = IO[A]
  override type PreMat[A] = IO[A]
}
