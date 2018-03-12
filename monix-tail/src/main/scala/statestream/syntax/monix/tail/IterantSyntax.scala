package statestream.syntax.monix.tail

import monix.eval.Task
import monix.tail.Iterant
import statestream.syntax.WriterStreamOps

trait IterantSyntax extends WriterStreamOps {
  override type Stream[A] = Iterant[Task, A]
  override type Async[A] = Task[A]
  override type PreMat[A] = Task[A]
}
