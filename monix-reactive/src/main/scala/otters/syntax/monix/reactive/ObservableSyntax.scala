package otters.syntax.monix.reactive

import monix.eval.Task
import monix.reactive.Observable
import otters.syntax.WriterStreamOps

trait ObservableSyntax extends WriterStreamOps {
  override type Stream[A] = Observable[A]
  override type Async[A] = Task[A]
  override type PreMat[A] = Task[A]
}
