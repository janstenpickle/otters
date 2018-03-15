package otters.instances

import akka.NotUsed
import akka.stream.scaladsl.Source

package object akkastream extends AkkaStreamInstances {
  type Src[A] = Source[A, NotUsed]
}
