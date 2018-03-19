package otters.syntax.akkastream

import akka.stream.scaladsl.Sink
import otters.instances.akkastream.Flw
import otters.syntax.{EitherTExtendedSyntax, EitherTSyntax}

trait AkkaStreamEitherTSyntax extends EitherTSyntax with EitherTExtendedSyntax[Flw, Sink]
