import Dependencies._

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.example",
      scalaVersion := "2.12.4",
      version      := "0.1.0-SNAPSHOT",
      scalacOptions += "-Ypartial-unification"
    )),
    name := "stream-state",
    libraryDependencies ++= Seq(
      akkaStreams,
      cats,
      scalaTest % Test
    ),
    addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.4")
  )
