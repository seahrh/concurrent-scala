lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "retry",
      scalaVersion := "2.12.3",
      version      := "1.0.0"
    )),
    name := "retry",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % versions.scalatest
    )
  )
lazy val versions = new {
  val scalatest = "3.0.4"
}
wartremoverErrors ++= Warts.allBut(
  Wart.Enumeration,
  Wart.Equals,
  Wart.ToString,
  Wart.Throw,
  Wart.DefaultArguments,
  Wart.Return,
  Wart.TraversableOps,
  Wart.ImplicitParameter,
  Wart.NonUnitStatements
)
