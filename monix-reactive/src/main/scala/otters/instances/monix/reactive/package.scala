package otters.instances.monix

import monix.eval.Task
import monix.reactive.Observable

package object reactive extends ObservableInstances {
  type Pipe[A, B] = Observable[A] => Observable[B]
  type Sink[A, B] = Observable[A] => Task[B]
}
