package prchecklist.infrastructure

import java.io.ByteArrayInputStream
import java.net.URI

import prchecklist.utils.AppConfig

import org.json4s
import org.json4s.{Formats => JsonFormats}
import org.json4s.jackson.JsonMethods

import com.redis.RedisClient
import com.redis.serialization.{ Format => RedisFormat, Parse => RedisParse }

import scalaz.Monad
import scalaz.syntax.monad._ // M.map(v) => v.map

import scala.language.higherKinds

trait RedisComponent {
  self: AppConfig =>

  def redis: Redis

  class Redis {
    // Convert implicit JsonFormats to RedisParse (redis.serialization.Parse)
    implicit def redisParseJson[A](implicit formats: JsonFormats, mf: Manifest[A]) = RedisParse {
      b => JsonMethods.parse(new ByteArrayInputStream(b)).extract[A]
    }

    def getOrUpdate[A <: AnyRef, M[_]](key: String, expireIn: scala.concurrent.duration.Duration)(ifNotFound: => M[(A, Boolean)])(implicit M: Monad[M], rf: RedisFormat, jf: JsonFormats = json4s.jackson.Serialization.formats(json4s.NoTypeHints), mf: Manifest[A]): M[A] = {
      val redis = mkRedis()
        redis.get[A](key) match {
          case Some(v) =>
            M.pure(v)

            case None =>
            ifNotFound.map {
              case (v, ok) =>
                if (ok) redis.setex(key, expireIn.toSeconds, json4s.jackson.Serialization.write(v))
                  v
            }
        }
    }

    private def mkRedis(): RedisClient = {
      val u = new URI(redisUrl)
        new RedisClient(host = u.getHost, port = u.getPort)
    }
  }
}
