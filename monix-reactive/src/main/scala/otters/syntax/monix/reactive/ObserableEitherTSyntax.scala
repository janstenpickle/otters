package otters.syntax.monix.reactive

import otters.instances.monix.reactive.{Pipe, Sink}
import otters.syntax.{EitherTExtendedSyntax, EitherTSyntax}

trait ObserableEitherTSyntax extends EitherTSyntax with EitherTExtendedSyntax[Pipe, Sink]
