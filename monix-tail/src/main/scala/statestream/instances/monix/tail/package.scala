package statestream.instances.monix

import cats.Id
import monix.tail.Iterant
import statestream.WriterStream

package object tail extends IterantInstances {
  type IterantWriterStream[F[_], S, A] = WriterStream[Iterant[F, ?], Id, F, F, S, A]
}
