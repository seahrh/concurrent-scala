lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "retry",
      scalaVersion := "2.12.3",
      version      := "1.0.0"
    )),
    name := "retry",
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.3"
  )
