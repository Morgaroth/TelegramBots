import io.github.morgaroth.sbt.commons.Libraries.{Akka, Joda, Spray}
import io.github.morgaroth.sbt.commons.Repositories

name := "MorgarothTestBot"

version := "1.0"

scalaVersion := "2.11.7"

resolvers ++= Seq(
  Resolver.sonatypeRepo("releases")
)

libraryDependencies ++= Seq(
  Spray.Client.`1.3.3`,
  Spray.Json.`1.3.2`,
  Spray.JsonAnnotation.`0.4.2`,
  Joda.Time.`2.8.2`,
  Joda.Convert.`1.7`,
  Akka.Actor.`2.3.12`
)

addCompilerPlugin(Paradise.`2.1.0-M5`)

seq(Revolver.settings: _*)