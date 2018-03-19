package otters.syntax.monix.tail

import monix.eval.Task
import monix.tail.Iterant
import otters.syntax.{WriterTExtendedSyntax, WriterTSyntax}
import otters.{FunctionPipe, FunctionSink}

trait IterantTaskWriterTSyntax
    extends WriterTSyntax
    with WriterTExtendedSyntax[FunctionPipe[Task, ?, ?], FunctionSink[Iterant[Task, ?], Task, ?, ?]]
