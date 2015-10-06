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

def keepClass(className: String) = s"-keep class $className"

def keepClassWithConstructor(className: String) =
  s"""-keepclasseswithmembers class $className {
     |    <init>(...);
     |}""".stripMargin

val notRequired =
  """-keep class scala.collection.immutable.StringLike {
    |  *;
    |}
    |-keepclasseswithmembers class * {
    |  public <init>(java.lang.String, akka.actor.ActorSystem$Settings, akka.event.EventStream, akka.actor.Scheduler, akka.actor.DynamicAccess);
    |}
    |-keepclasseswithmembers class * {
    |  public <init>(akka.actor.ExtendedActorSystem);
    |}
    |-keep class scala.collection.SeqLike {
    |  public protected *;
    |}
    |-keepclassmembernames class * implements akka.actor.Actor {
    |  akka.actor.ActorContext context;
    |  akka.actor.ActorRef self;
    |}
    |-keep class * implements akka.actor.ExtensionId {
    |  public <init>(...);
    |}
    |-keep class * implements akka.actor.ExtensionIdProvider {
    |  public <init>(...);
    |}
    |-keep class akka.actor.SerializedActorRef {
    |  *;
    |}
    |-keep class * implements akka.actor.SupervisorStrategyConfigurator {
    |  public <init>(...);
    |}
    |-keep class * extends akka.dispatch.ExecutorServiceConfigurator {
    |  public <init>(...);
    |}
    |-keep class * extends akka.dispatch.MessageDispatcherConfigurator {
    |  public <init>(...);
    |}
    |-keep class akka.remote.DaemonMsgCreate {
    |  *;
    |}
    |-keep class * extends akka.remote.RemoteTransport {
    |  public <init>(...);
    |}
    |-keep class * implements akka.routing.RouterConfig {
    |  public <init>(...);
    |}
    |-keep class * implements akka.serialization.Serializer {
    |  public <init>(...);
    |}
    |-keepclasseswithmembers class io.github.morgaroth.telegram.** {
    |  public <init>(...);
    |}
    |
    |""".stripMargin

val required =
  """
    |-keep class akka.actor.LocalActorRefProvider$Guardian {
    |  public <init>(...);
    |}
    |-keep class akka.actor.LocalActorRefProvider$SystemGuardian {
    |  public <init>(...);
    |}
    |-keep class * implements akka.actor.ActorRefProvider {
    |  public <init>(...);
    |}
    |-keepclasseswithmembers class * implements akka.actor.Actor {
    |  <init>(...);
    |  akka.actor.ActorContext context;
    |  akka.actor.ActorRef self;
    |}
    |-keep class * implements akka.dispatch.MailboxType {
    |  public <init>(...);
    |}
    |-keep class akka.event.Logging*
    |-keep class akka.event.Logging$LogExt {
    |  public <init>(...);
    |}
    |""".stripMargin

ProguardKeys.options in Proguard ++= Seq(
  "-dontnote",
  "-dontobfuscate",
  "-dontwarn",
  "-ignorewarnings",
  //  "-optimizations !code/allocation/variable",
  "-dontoptimize",
  keepMain("io.github.morgaroth.telegram.bot.botserver.BotServer"),
  keepClass("com.typesafe.**"),
  keepClass("akka.**"),
  required,
  keepClassWithConstructor("akka.actor.LightArrayRevolverScheduler")
  //  keepClassWithConstructor("akka.dispatch.*"),
  //  keepClassWithConstructor("akka.dispatch.BoundedMessageQueueSemantics"),
  //  keepClassWithConstructor("akka.dispatch.UnboundedMessageQueueSemantics"),
  //  keepClassWithConstructor("akka.dispatch.DequeBasedMessageQueueSemantics"),
  //  keepClassWithConstructor("akka.dispatch.BoundedDequeBasedMessageQueueSemantics"),
  //  keepClassWithConstructor("akka.dispatch.UnboundedDequeBasedMessageQueueSemantics"),
  //  keepClassWithConstructor("akka.actor.LocalActorRefProvider"),
  //  keepClassWithConstructor("com.typesafe.config.impl.SimpleConfigOrigin")
)

ProguardKeys.proguardVersion in Proguard := "5.1"

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