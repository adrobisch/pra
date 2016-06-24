enablePlugins(JavaAppPackaging)

name := "price-ratio-analyser"

scalaVersion := "2.11.8"

maintainer := "Andreas Drobisch"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % "2.4.7",
  "org.scalatest" %% "scalatest" % "2.2.6" % "test"
)