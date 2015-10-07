import com.typesafe.sbt.SbtProguard.ProguardOptions.keepMain
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

assemblyJarName := s"fat-bots-" + version.value + ".jar"

//mainClass := Some("io.github.morgaroth.telegram.bot.botserver.BotServer")

mainClass := Some("io.github.morgaroth.telegram.bot.botserver.DevBotServer")

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

def keepClass(className: String) = s"-keep class $className"

def keepCassWithMembers(cn: String)(fields: String*) =
  s"""-keepclasseswithmembers $cn {
  |${fields.mkString("|  ","\n|  ","")}
  |}""".stripMargin

def keepClassWithConstructor(className: String) = s"-keep class $className { <init>(...); }"

def keepClassWithAllMembers(cn: String) = s"-keep class $cn { *; }"

val req = Seq(
  keepClassWithConstructor("akka.actor.LocalActorRefProvider$Guardian"),
  keepClassWithConstructor("akka.actor.LocalActorRefProvider$SystemGuardian"),
  keepClassWithConstructor("spray.can.HttpExt"),
  keepClassWithConstructor("akka.routing.RoutedActorCell$RouterActorCreator"),
  keepClassWithConstructor("akka.io.TcpOutgoingConnection"),
  keepClassWithConstructor("akka.io.TcpManager"),
  //  keepClassWithAllMembers("scala.concurrent.forkjoin.ForkJoinPool"),
  keepClassWithConstructor("akka.event.Logging$LogExt"),
  keepClassWithConstructor("akka.actor.LightArrayRevolverScheduler"),
  keepClass("akka.dispatch.*MessageQueueSemantics")
)

val required =
  """
    |-verbose
    |-keep class * implements akka.actor.ActorRefProvider { public <init>(...); }
    |-keepclasseswithmembers class * implements akka.actor.Actor {
    |  <init>(...);
    |  akka.actor.ActorContext context;
    |  akka.actor.ActorRef self;
    |}
    |-keep class * implements akka.dispatch.MailboxType {
    |  public <init>(...);
    |}
    |-keep class akka.event.Logging*
    |
    |-keep class spray.http.** { *; }
    |
    |-keepclasseswithmembers class io.github.morgaroth.telegram.bot.core.** { *; }
    |
    |#-keepclasseswithmembers class akka.dispatch.ForkJoinExecutorConfigurator$AkkaForkJoinPool {
    |-keepclasseswithmembers class scala.concurrent.forkjoin.ForkJoinPool {
    |  long ctl;
    |  long stealCount;
    |  int plock;
    |  int indexSeed;
    |}
    |-keepclasseswithmembers class java.lang.Thread {
    |  java.lang.Object parkBlocker;
    |}
    |-keepclasseswithmembers class scala.concurrent.forkjoin.ForkJoinPool$WorkQueue {
    |  int qlock;
    |}
    |-keepclasseswithmembers class scala.concurrent.forkjoin.ForkJoinTask {
    |  int status;
    |}
    | """.stripMargin

ProguardKeys.options in Proguard ++= req ++ Seq(
  "-dontnote",
  //      "-dontobfuscate",
  "-keepattributes Signature",
  "-dontwarn",
  "-keepattributes SourceFile,LineNumberTable",
  "-printmapping mappings.txt",
  "-ignorewarnings",
  "-optimizations !code/allocation/variable",
  //  "-dontoptimize",
  //  keepMain("io.github.morgaroth.telegram.bot.botserver.BotServer"),
  keepMain("io.github.morgaroth.telegram.bot.botserver.DevBotServer"),
  //  keepClass("com.typesafe.**"),
  //  keepClass("akka.**"),
  required,
  "-keep class akka.actor.DefaultSupervisorStrategy",
  //  "-keep class akka.dispatch.BoundedDequeBasedMessageQueueSemantics { *; }",
  //  "-keep class akka.dispatch.UnboundedDequeBasedMessageQueueSemantics { *; }",
  //  "-keep class akka.dispatch.DequeBasedMessageQueueSemantics { *; }",
  "-keep class akka.dispatch.MultipleConsumerSemantics"
)

ProguardKeys.proguardVersion in Proguard := "5.2.1"

javaOptions in(Proguard, proguard) := Seq("-Xmx2G")

ProguardKeys.outputs in Proguard := Seq(
  (crossTarget in Compile).value / (s"bots-" + version.value + ".jar")
)

ProguardKeys.inputs in Proguard := Seq(
  (crossTarget in Compile).value / (assemblyJarName in assembly).value
)

//proguard in Proguard <<= (proguard in Proguard) dependsOn assembly

ProguardKeys.inputFilter in Proguard := { file =>
  file.name match {
    case programjar if programjar == (assemblyJarName in assembly).value => None
    case _ => Some("!META-INF/**")
  }
}