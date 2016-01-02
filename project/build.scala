import sbt._
import Keys._
import org.scalatra.sbt._
import org.scalatra.sbt.PluginKeys._
import com.mojolly.scalate.ScalatePlugin._
import ScalateKeys._
import com.typesafe.sbt.SbtScalariform._

object GithubReleasePullRequestsChecklistBuild extends Build {
  val Organization = "net.tokyoenvious"
  val Name = "GitHub Release Pull Requests Checklist"
  val Version = "0.1.0-SNAPSHOT"
  val ScalaVersion = "2.11.7"
  val ScalatraVersion = "2.4.0.RC3"

  lazy val project = Project (
    "github-release-pull-requests-checklist",
    file("."),
    settings = ScalatraPlugin.scalatraSettings ++ scalateSettings ++ scalariformSettings ++ Seq(
      organization := Organization,
      name := Name,
      version := Version,
      scalaVersion := ScalaVersion,
      scalacOptions += "-deprecation",
      scalacOptions += "-feature",
      resolvers += Classpaths.typesafeReleases,
      resolvers += "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases",
      libraryDependencies ++= Seq(
        "org.scalatra" %% "scalatra" % ScalatraVersion,
        "org.scalatra" %% "scalatra-scalate" % ScalatraVersion,
        "org.scalatra" %% "scalatra-scalatest" % ScalatraVersion % "test",
        "ch.qos.logback" % "logback-classic" % "1.1.2" % "runtime",
        "org.eclipse.jetty" % "jetty-webapp" % "9.2.10.v20150310" % "container",
        "javax.servlet" % "javax.servlet-api" % "3.1.0" % "provided",
        "org.scalaj" %% "scalaj-http" % "1.1.6",
        "org.json4s" %% "json4s-native" % "3.3.0",
        "org.scalaz" %% "scalaz-core" % "7.1.4",
        "org.scalaz" %% "scalaz-concurrent" % "7.1.4",
        "com.typesafe.slick" % "slick_2.11" % "3.0.0",
        "org.postgresql" % "postgresql" % "9.4.1207",
        "com.github.tarao" %% "slick-jdbc-extension" % "0.0.3",
        "net.debasishg" %% "redisclient" % "3.1"
      ),
      scalateTemplateConfig in Compile <<= (sourceDirectory in Compile){ base =>
        Seq(
          TemplateConfig(
            base / "webapp" / "WEB-INF" / "templates",
            Seq.empty,  /* default imports should be added here */
            Seq(
              Binding("context", "_root_.org.scalatra.scalate.ScalatraRenderContext", importMembers = true, isImplicit = true)
            ),  /* add extra bindings here */
            Some("templates")
          )
        )
      },
      fork in Test := true,
      javaOptions in Test += "-Ddatabase.url=jdbc:postgresql:prchecklist_test",
      testOptions in Test += Tests.Setup(
        () => {
          import scala.sys.process._
          import scala.language.postfixOps

          "dropdb prchecklist_test" #&&
          "createdb prchecklist_test" #&&
          "psql prchecklist_test -f db/prchecklist.sql" !!
        }
      ),
      watchSources ~= {
        _.filterNot {
          f =>
            """^\..*\.sw.$""".r.findFirstIn(f.getName).isDefined || f.isDirectory
        }
      }
    )
  )

  import scalariform.formatter.preferences._

  ScalariformKeys.preferences := ScalariformKeys.preferences.value
    .setPreference(PreserveDanglingCloseParenthesis, true)
}
