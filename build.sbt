import org.scalatra.sbt.ScalatraPlugin
import com.mojolly.scalate.ScalatePlugin
import com.typesafe.sbt.SbtScalariform
import NativePackagerHelper._

val stylesheetsDirectory = settingKey[File]("Directory where generated stylesheets are placed")

lazy val prchecklist = (project in file(".")).
  enablePlugins(
    BuildInfoPlugin,
    JavaAppPackaging
  ).
  settings(Defaults.defaultSettings).
  settings(ScalatraPlugin.scalatraWithJRebel).
  settings(ScalatePlugin.scalateSettings).
  settings(SbtScalariform.scalariformSettings).
  settings(
      organization := "net.tokyoenvious",
      name := "prchecklist",
      scalaVersion := "2.11.7",
      version := {
        ("git describe --tags --match v* --dirty=-SNAPSHOT" !!) trim
      },

      scalacOptions ++= Seq(
        "-unchecked",
        "-deprecation",
        "-feature"
      ),

      resolvers += Classpaths.typesafeReleases,
      resolvers += "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases",

      libraryDependencies ++= Seq(
        "org.scalatra" %% "scalatra" % "2.4.0",
        "org.scalatra" %% "scalatra-scalate" % "2.4.0",
        "org.scalatra" %% "scalatra-scalatest" % "2.4.0" % "test",
        "ch.qos.logback" % "logback-classic" % "1.1.2" % "runtime",
        "org.eclipse.jetty" % "jetty-webapp" % "9.2.10.v20150310" % "container;compile",
        "javax.servlet" % "javax.servlet-api" % "3.1.0" % "provided",
        "org.scalaj" %% "scalaj-http" % "1.1.6",
        "org.json4s" %% "json4s-jackson" % "3.3.0",
        "org.scalaz" %% "scalaz-core" % "7.1.4",
        "org.scalaz" %% "scalaz-concurrent" % "7.1.4",
        "com.typesafe.slick" %% "slick" % "3.0.0",
        "org.postgresql" % "postgresql" % "9.4.1207",
        "com.github.tarao" %% "slick-jdbc-extension" % "0.0.3",
        "net.debasishg" %% "redisclient" % "3.1",
        "org.pegdown" % "pegdown" % "1.6.0",
        "org.mockito" % "mockito-core" % "2.0.36-beta" % "test"
      )
    ).
    settings(
      ScalateKeys.scalateTemplateConfig in Compile <<= (sourceDirectory in Compile) { base =>
        Seq(
          TemplateConfig(
            base / "webapp" / "WEB-INF" / "templates",
            Seq(
              "import prchecklist.views.Helper._",
              "import prchecklist.BuildInfo"
            ),
            Seq(
              Binding("context", "_root_.org.scalatra.scalate.ScalatraRenderContext", importMembers = true, isImplicit = true)
            ),
            Some("templates")
          )
        )
      }
    ).
    settings(
      fork in Test := true,
      javaOptions in Test ++= Seq(
        "-Ddatabase.url=jdbc:postgresql:prchecklist_test",
        "-Dgithub.domain=github.com",
        "-Dgithub.clientId=",
        "-Dgithub.clientSecret="
      ),
      testOptions in Test += Tests.Setup(
        () => {
          import scala.sys.process._
          import scala.language.postfixOps

          "dropdb prchecklist_test" ###
          "createdb prchecklist_test" #&&
          "psql prchecklist_test -f db/prchecklist.sql" !!

          "redis-cli FLUSHDB" !!
        }
      )
    ).
    settings(
      sourceGenerators in Compile <+= buildInfo in Compile,
      buildInfoKeys := Seq[BuildInfoKey](
        name, version, scalaVersion, sbtVersion
      ),
      buildInfoOptions += BuildInfoOption.BuildTime,
      buildInfoPackage := "prchecklist"
    ).
    settings (
      update <<= (update, baseDirectory, cacheDirectory, streams) map {
        (report, base, cache, s) =>
          val npmInstall = FileFunction.cached(cache / "npm-install") (FilesInfo.hash, FilesInfo.exists) {
            (changeReport, in) =>
              s.log.info("Running 'npm install' ...")
              ("npm" :: "install" :: Nil).!
              s.log.info("Done.")

              Set[File]()
          }

          npmInstall(Set(base / "package.json"))

          report
      }
    ).
   settings(
     // We could have used webappSrc key provided by xsbt-web-plugin,
     // but it is a TaskKey which a SettingKey cannot depend on.
     stylesheetsDirectory := (sourceDirectory in Compile).value / "webapp" / "stylesheets", /* src/main/webapp/stylesheets */
     resourceGenerators in Compile <+= (stylesheetsDirectory, streams) map {
       (stylesheetsDirectory, s) =>
         s.log.info("Running 'npm run build' ...")
         ("npm" :: "run" :: "build" :: Nil).!
         s.log.info("Done.")

         Seq(
           stylesheetsDirectory / "main.css"
         )
     },
     cleanFiles += stylesheetsDirectory.value,
     mappings in Universal ++= {
       (stylesheetsDirectory.value).*** x relativeTo(baseDirectory.value)
     }
   )

addCommandAlias("devel", Seq(
  "set javaOptions += \"-DbrowserSync.port=3000\"",
  "processStart project/tools/npm-run-script-wrapper watch",
  "~re-start",
  "processStopAll"
).mkString(";",";",""))

watchSources ~= {
  _.filterNot {
    f =>
      """\.(less|css(\.map)?)$""".r.findFirstIn(f.getName).isDefined
  }
}

