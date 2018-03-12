package otters.instances.monix

import cats.Id
import monix.eval.Task
import monix.reactive.Observable
import otters.WriterStream

package object reactive extends ObservableInstances {
  type ObservableWriterStream[S, A] = WriterStream[Observable, Id, Task, Task, S, A]
}
