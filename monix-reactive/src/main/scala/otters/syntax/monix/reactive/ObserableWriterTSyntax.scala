package otters.syntax.monix.reactive

import otters.instances.monix.reactive.{Pipe, Sink}
import otters.syntax.{WriterTExtendedSyntax, WriterTSyntax}

trait ObserableWriterTSyntax extends WriterTSyntax with WriterTExtendedSyntax[Pipe, Sink]
