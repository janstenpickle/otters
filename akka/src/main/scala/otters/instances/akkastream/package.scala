package otters.instances

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Sink, Source}

package object akkastream extends AkkaStreamInstances {
  type Src[A] = Source[A, _]
  type Flw[A, B] = Flow[A, B, NotUsed]
}
