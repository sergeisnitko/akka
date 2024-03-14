
package akka.persistence.journal.inmem

import akka.util.ByteString
import akka.serialization.SerializationExtension
import akka.serialization.Serialization
import akka.actor.ActorSystem
import akka.persistence._

import scala.reflect._
import scala.util.Try

import redis.ByteStringFormatter

import spray.json._
import java.time.ZonedDateTime

case class Journal(sequenceNr: Long, persistentRepr: Array[Byte], deleted: Boolean, createdAt: ZonedDateTime)

object Journal extends DefaultJsonProtocol {
  implicit val zonedDateTimeFormat: JsonFormat[ZonedDateTime] = new JsonFormat[ZonedDateTime] {
    override def read(json: JsValue): ZonedDateTime = json match {
      case JsString(str) ⇒ ZonedDateTime.parse(str)
      case _             ⇒ throw new DeserializationException("ZonedDateTime expected")
    }

    override def write(obj: ZonedDateTime): JsValue = JsString(obj.toString)
  }

  implicit val fmt = jsonFormat4(Journal.apply)

  implicit val byteStringFormatter = new ByteStringFormatter[Journal] {
    override def serialize(data: Journal): ByteString = {
      ByteString(data.toJson.toString)
    }

    override def deserialize(bs: ByteString): Journal = {
      try {
        bs.utf8String.parseJson.convertTo[Journal]
      } catch {
        case e: Exception ⇒ throw SerializationException("Error deserializing Journal.", e)
      }
    }
  }
}

case class SerializationException(message: String, cause: Throwable) extends Throwable(message, cause)

trait ByteArraySerializer {
  implicit val actorSystem: ActorSystem

  private val serialization = SerializationExtension(actorSystem)

  def toBytes(data: AnyRef): Try[Array[Byte]] = serialization.serialize(data)

  def fromBytes[T: ClassTag](a: Array[Byte]): Try[T] =
    serialization.deserialize(a, classTag[T].runtimeClass.asInstanceOf[Class[T]])
}