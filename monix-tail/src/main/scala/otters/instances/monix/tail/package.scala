package otters.instances.monix

import cats.Id
import monix.tail.Iterant
import otters.WriterStream

package object tail extends IterantInstances {
  type IterantWriterStream[F[_], S, A] = WriterStream[Iterant[F, ?], Id, F, F, S, A]
}
