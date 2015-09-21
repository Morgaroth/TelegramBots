import io.github.morgaroth.sbt.commons.Libraries.{Akka, Joda, Spray}
import io.github.morgaroth.sbt.commons.Repositories

import scala.io.Source

name := "MorgarothTestBot"

version := "1.0"

scalaVersion := "2.11.7"

resolvers ++= Seq(
  Resolver.sonatypeRepo("releases")
)

val Server = config("server") extend Compile

inConfig(Server)(
  baseAssemblySettings ++
    inTask(assembly)(mainClass := Some("io.github.morgaroth.telegram.bot.test.WebServer")) ++
    inTask(assembly)(assemblyJarName := s"test-bots-server-" + version.value + ".jar")
)

val Standalone = config("standalone") extend Compile

inConfig(Standalone)(
  baseAssemblySettings ++
    inTask(assembly)(mainClass := Some("io.github.morgaroth.telegram.bot.test.TestApp")) ++
    inTask(assembly)(assemblyJarName := s"test-bots-standalone-" + version.value + ".jar")
)

libraryDependencies ++= Seq(
  Spray.Client.`1.3.3`,
  Spray.Routing.`1.3.3`,
  Spray.Json.`1.3.2`,
  Spray.JsonAnnotation.`0.4.2`,
  Joda.Time.`2.8.2`,
  Joda.Convert.`1.7`,
  Akka.Actor.`2.3.12`,
  Ficus.Config.`1.1.2`
)

addCompilerPlugin(Paradise.`2.1.0-M5`)

seq(Revolver.settings: _*)

enablePlugins(SbtCommons)