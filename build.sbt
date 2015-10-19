import com.typesafe.sbt.SbtProguard.ProguardOptions.keepMain
import com.typesafe.sbt.SbtProguard._
import com.typesafe.sbt.SbtProguard.ProguardKeys.{options => proguardOptions, proguard}
import com.typesafe.sbt.SbtProguard.ProguardSettings._
import sbtassembly.AssemblyPlugin.autoImport._

name := "TelegramBots"

version := "2.5"

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

mainClass := Some("io.github.morgaroth.telegram.bot.botserver.BotServer")

//mainClass := Some("io.github.morgaroth.telegram.bot.botserver.DevBotServer")

libraryDependencies ++= Seq(
  Spray.Client.`1.3.3`,
  Spray.Routing.`1.3.3`,
  Spray.Json.`1.3.2`,
  Spray.JsonAnnotation.`0.4.2`,
  Joda.Time.`2.8.2`,
  Joda.Convert.`1.7`,
  Akka.Actor.`2.3.12`,
  Akka.Stream.`1.0`,
  Ficus.Config.`1.1.2`,
  Morgaroth.UtilsMongo.`2.0.0` withSources(),
  Pathikrit.BetterFiles.`2.6.1`,
  Tumblr.Jumblr.`0.0.11`
)

addCompilerPlugin(Paradise.`2.1.0-M5`)

seq(Revolver.settings: _*)

enablePlugins(SbtCommons)

proguardSettings

val akka =
  """
    |-keep class akka.actor.LocalActorRefProvider$Guardian { <init>(...); }
    |-keep class akka.actor.LocalActorRefProvider$SystemGuardian { <init>(...); }
    |-keep class spray.can.HttpExt { <init>(...); }
    |-keep class akka.routing.RoutedActorCell$RouterActorCreator { <init>(...); }
    |-keep class akka.io.TcpOutgoingConnection { <init>(...); }
    |-keep class akka.io.TcpManager { <init>(...); }
    |-keep class akka.event.Logging$LogExt { <init>(...); }
    |-keep class akka.actor.LightArrayRevolverScheduler { <init>(...); }
    |-keep class akka.dispatch.*MessageQueueSemantics
    |-keep class * implements akka.actor.ActorRefProvider { public <init>(...); }
    |-keepclasseswithmembers class * implements akka.actor.Actor {
    |  <init>(...);
    |  akka.actor.ActorContext context;
    |  akka.actor.ActorRef self;
    |}
    |-keep class * implements akka.dispatch.MailboxType { public <init>(...); }
    |-keep class akka.event.Logging*
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
    |
    |-keep class akka.actor.DefaultSupervisorStrategy
    |-keep class akka.dispatch.MultipleConsumerSemantics
    | """.stripMargin

val spray =
  """
    |-keep class spray.http.** { *; }
    |
  """.stripMargin


val debug =
  """
    |-dontoptimize
    |#-dontobfuscate
    |-keepattributes SourceFile,LineNumberTable
  """.stripMargin

val modificators =
  """
    |-verbose
    |-dontnote
    |-keepattributes Signature,*Annotation*
    |-dontwarn
    |-printmapping mappings.txt
    |-ignorewarnings
    |-optimizations !code/allocation/variable
    | """.stripMargin

val tumblr =
  """
    |-keepclasseswithmembers class com.tumblr.jumblr.types.Photo$PhotoType { *; }
    |-keepclasseswithmembers class com.tumblr.jumblr.responses.ResponseWrapper { 
    |  com.google.gson.JsonElement response;
    |}
    |-keepclasseswithmembers class com.tumblr.jumblr.types.Photo {
    |  private <fields>;
    | }
    |-keepclasseswithmembers class com.tumblr.jumblr.types.PhotoPost {
    |  private <fields>;
    |}
    |-keepclasseswithmembers class com.tumblr.jumblr.types.PhotoSize {
    | private <fields>;
    |}
    |-keepclasseswithmembers class com.tumblr.jumblr.types.Blog {
    |  private <fields>;
    |}
    |-keepclasseswithmembers class com.tumblr.jumblr.types.Post {
    | private <fields>;
    |}
  """.stripMargin

val program =
  """
    |-keep class org.slf4j.ILoggerFactory
    |-keepclasseswithmembers class io.github.morgaroth.telegram.bot.** { *; }
    |-keep class org.slf4j.impl.StaticLoggerBinder
    | """.stripMargin

val mongoProguard =
  """
    |-keepclassmembers class com.mongodb.casbah.Implicits$$anon$4 {
    |  com.mongodb.casbah.MongoCollection asScala();
    |}
    |-keepclassmembers class com.mongodb.casbah.Implicits$$anon$5 {
    |  com.mongodb.casbah.MongoDB asScala();
    |}
    |
    |-keep interface com.mongodb.ConnectionPoolStatisticsMBean
    |-keepclasseswithmembers class com.mongodb.ConnectionPoolStatistics { *; }
    | """.stripMargin

ProguardKeys.options in Proguard ++= Seq(
  keepMain("io.github.morgaroth.telegram.bot.botserver.BotServer"),
  //  keepMain("io.github.morgaroth.telegram.bot.botserver.DevBotServer"),
  spray,
  program,
  tumblr,
//  debug,
  modificators,
  akka,
  mongoProguard
)

ProguardKeys.proguardVersion in Proguard := "5.2.1"

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