import com.typesafe.sbt.SbtProguard._
import com.typesafe.sbt.SbtProguard.ProguardKeys.{options => proguardOptions, proguard}
import com.typesafe.sbt.SbtProguard.ProguardSettings._
import sbtassembly.AssemblyPlugin.autoImport._

name := "TelegramBots"

version := "2.2"

scalaVersion := "2.11.7"

resolvers ++= Seq(
  Resolver.sonatypeRepo("releases"),
  Pathikrit.repository
)
//
//val Server = config("server") extend Compile
//
//inConfig(Server)(
//  baseAssemblySettings ++
//    inTask(assembly)(mainClass := Some("io.github.morgaroth.telegram.bot.test.WebServer")) ++
//    inTask(assembly)(assemblyJarName := s"bots-server-" + version.value + ".jar")
//)

baseAssemblySettings

//mainClass := Some("io.github.morgaroth.telegram.bot.botserver.BotServer")

assemblyJarName := s"fat-bots-" + version.value + ".jar"

mainClass := Some("io.github.morgaroth.telegram.bot.botserver.BotServer")

libraryDependencies ++= Seq(
  Spray.Client.`1.3.3`,
  Spray.Routing.`1.3.3`,
  Spray.Json.`1.3.2`,
  Spray.JsonAnnotation.`0.4.2`,
  Joda.Time.`2.8.2`,
  Joda.Convert.`1.7`,
  Akka.Actor.`2.3.12`,
  Ficus.Config.`1.1.2`,
  Morgaroth.UtilsMongo.`1.2.10`,
  Pathikrit.BetterFiles.`2.6.1`,
  "com.tumblr" % "jumblr" % "0.0.11"
)

addCompilerPlugin(Paradise.`2.1.0-M5`)

seq(Revolver.settings: _*)

enablePlugins(SbtCommons)

proguardSettings

ProguardKeys.options in Proguard ++= Seq(
  "-dontnote"
  , "-dontwarn"
  , "-ignorewarnings"
)

ProguardKeys.options in Proguard += ProguardOptions.keepMain("io.github.morgaroth.telegram.bot.botserver.BotServer")

ProguardKeys.proguardVersion in Proguard := "5.1"

javaOptions in(Proguard, proguard) := Seq("-Xmx2G")

ProguardKeys.outputs in Proguard := Seq(
  (crossTarget in Compile).value / (s"bots-" + version.value + ".jar")
)

ProguardKeys.inputs in Proguard := Seq(
  (crossTarget in Compile).value / (assemblyJarName in assembly).value
)

proguard in Proguard <<= (proguard in Proguard) dependsOn assembly

ProguardKeys.inputFilter in Proguard := { file =>
  file.name match {
    case programjar if programjar == (assemblyJarName in assembly).value => None
    case _ => Some("!META-INF/**")
  }
}