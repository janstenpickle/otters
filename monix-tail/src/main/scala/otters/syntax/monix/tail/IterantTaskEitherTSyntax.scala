package otters.syntax.monix.tail

import monix.eval.Task
import monix.tail.Iterant
import otters.{FunctionPipe, FunctionSink}
import otters.syntax.{EitherTExtendedSyntax, EitherTSyntax}

trait IterantTaskEitherTSyntax
    extends EitherTSyntax
    with EitherTExtendedSyntax[FunctionPipe[Task, ?, ?], FunctionSink[Iterant[Task, ?], Task, ?, ?]]
