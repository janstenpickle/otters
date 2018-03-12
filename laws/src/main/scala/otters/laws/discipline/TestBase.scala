package otters.laws.discipline

import cats.Eq
import cats.effect.laws.util.{TestContext, TestInstances}
import org.scalatest.FunSuite
import org.typelevel.discipline.Laws
import org.typelevel.discipline.scalatest.Discipline

trait TestBase extends FunSuite with Discipline with TestInstances with TestUtils {
  implicit def seqEq[A](implicit ev: Eq[List[A]]): Eq[Seq[A]] = Eq.by(_.toList)

  def checkAllAsync(name: String, f: TestContext => Laws#RuleSet) {
    val context = TestContext()
    val ruleSet = f(context)

    for ((id, prop) ← ruleSet.all.properties)
      test(name + "." + id) {
        silenceSystemErr(check(prop))
      }
  }
}
