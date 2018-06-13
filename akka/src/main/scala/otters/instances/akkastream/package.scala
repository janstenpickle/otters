package otters.instances

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Source}

package object akkastream extends AkkaStreamInstances {
  type Src[A] = Source[A, _]
  type Flw[A, B] = Flow[A, B, NotUsed]
}
