package otters.syntax.monix

import monix.eval.Task
import monix.tail.Iterant

package object tail {
  type IterantTask[A] = Iterant[Task, A]

  object either extends IterantTaskEitherTSyntax
  object writer extends IterantTaskWriterTSyntax
}
