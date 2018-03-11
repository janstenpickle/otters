package statestream.instances

import cats.Id
import statestream.WriterStream

package object fs2 extends Fs2Instances {
  type Fs2WriterStream[F[_], S, A] = WriterStream[_root_.fs2.Stream[F, ?], Id, F, F, S, A]
}
