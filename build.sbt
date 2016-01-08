import org.scalatra.sbt.ScalatraPlugin
import com.mojolly.scalate.ScalatePlugin
import com.typesafe.sbt.{SbtScalariform, SbtStartScript}

lazy val prchecklist = (project in file(".")).
  enablePlugins(
    BuildInfoPlugin,
    GitVersioning
  ).
  settings(Defaults.defaultSettings).
  settings(ScalatraPlugin.scalatraWithJRebel).
  settings(ScalatePlugin.scalateSettings).
  settings(SbtScalariform.scalariformSettings).
  settings(SbtStartScript.startScriptForClassesSettings).
  settings(
      organization := "net.tokyoenvious",
      name := "prchecklist",
      scalaVersion := "2.11.7",

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
        "org.json4s" %% "json4s-native" % "3.3.0",
        "org.scalaz" %% "scalaz-core" % "7.1.4",
        "org.scalaz" %% "scalaz-concurrent" % "7.1.4",
        "com.typesafe.slick" %% "slick" % "3.0.0",
        "org.postgresql" % "postgresql" % "9.4.1207",
        "com.github.tarao" %% "slick-jdbc-extension" % "0.0.3",
        "net.debasishg" %% "redisclient" % "3.1",
        "org.pegdown" % "pegdown" % "1.6.0"
      )
    ).
    settings(
      ScalateKeys.scalateTemplateConfig in Compile <<= (sourceDirectory in Compile){ base =>
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
      }
    ).
    settings(
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
    settings(
      git.useGitDescribe := true
    )
