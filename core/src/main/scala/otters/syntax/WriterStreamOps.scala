package otters.syntax

import cats.Id
import otters.WriterStream

trait WriterStreamOps {
  type Stream[A]
  type Async[A]
  type PreMat[A]
  type WriterStrm[S, A] = WriterStreamT[Id, S, A]
  type WriterStreamT[F[_], S, A] = WriterStream[Stream, F, Async, PreMat, S, A]
}
