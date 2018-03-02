import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.4"
  lazy val akkaStreams = "com.typesafe.akka" %% "akka-stream" % "2.5.11"
  lazy val cats = "org.typelevel" %% "cats-core" % "1.0.1"
}
