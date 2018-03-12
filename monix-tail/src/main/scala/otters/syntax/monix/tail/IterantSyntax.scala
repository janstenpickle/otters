package otters.syntax.monix.tail

import monix.eval.Task
import monix.tail.Iterant
import otters.syntax.WriterStreamOps

trait IterantSyntax extends WriterStreamOps {
  override type Stream[A] = Iterant[Task, A]
  override type Async[A] = Task[A]
  override type PreMat[A] = Task[A]
}
