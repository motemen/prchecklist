package prchecklist.services

import java.io.ByteArrayInputStream
import java.net.URI

import org.json4s
import org.json4s.{Formats => JsonFormats}
import org.json4s.native.JsonMethods

import com.redis._
import com.redis.serialization.{ Format => RedisFormat, Parse => RedisParse }

import scalaz.Monad
import scalaz.syntax.monad._ // M.map(v) => v.map

import scala.language.higherKinds

object Redis {
  implicit def redisParseJson[A](implicit formats: JsonFormats, mf: Manifest[A]) = RedisParse {
    b => JsonMethods.parse(new ByteArrayInputStream(b)).extract[A]
  }

  def mkRedis(): RedisClient = {
    val redisURL = new URI(System.getProperty("redis.url", "redis://127.0.0.1:6379"))
    new RedisClient(host = redisURL.getHost, port = redisURL.getPort)
  }

  def getOrUpdate[A <: AnyRef, M[_]](key: String)(ifNotFound: => M[(A, Boolean)])(implicit M: Monad[M], rf: RedisFormat, jf: JsonFormats, mf: Manifest[A]): M[A] = {
    val redis = mkRedis()
    redis.get[A](key) match {
      case Some(v) => M.pure(v)
      case None =>
        ifNotFound.map {
          case (v, ok) =>
            if (ok) redis.set(key, json4s.native.Serialization.write(v))
            v
        }
    }
  }
}
