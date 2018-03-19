package otters.syntax.akkastream

import akka.stream.scaladsl.Sink
import otters.instances.akkastream.Flw
import otters.syntax.{WriterTExtendedSyntax, WriterTSyntax}

trait AkkaStreamWriterTSyntax extends WriterTSyntax with WriterTExtendedSyntax[Flw, Sink]
