/**
 * Copyright (C) 2009-2014 Typesafe Inc. <http://www.typesafe.com>
 */

package akka.persistence.journal.inmem

import _root_.redis._

import commands._
import api._

import akka.actor._
import akka.util.ByteString
import akka.serialization.SerializationExtension
import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.journal.AsyncWriteProxy
import akka.persistence.journal.AsyncWriteTarget.{ ReplayFailure, ReplaySuccess, ReplayMessages }
import akka.persistence.PersistentRepr

import akka.util.Timeout

import com.typesafe.config.Config

import scala.util.{ Try, Success, Failure }
import scala.collection.immutable
import scala.collection.immutable.Seq
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

import java.time.{ ZonedDateTime, ZoneOffset }

class InmemJournal extends AsyncWriteJournal with ByteArraySerializer with ActorLogging {
  import AsyncWriteProxy.SetStore

  override implicit lazy val actorSystem = context.system

  private val config = actorSystem.settings.config

  implicit object longFormatter extends ByteStringDeserializer[Long] {
    def deserialize(bs: ByteString): Long =
      bs.utf8String.toLong
  }

  implicit def ec = context.system.dispatcher

  implicit val serialization = SerializationExtension(context.system)

  val timeout = Timeout(5 seconds)

  var redis: RedisClient = _

  val identifiersKey = "akka:journal:persistenceIds"
  override def preStart(): Unit = {
    log.info("[AKKA_PERS] STARTED InmemJournal")

    println(context.system)
    var redisHost = config.getString("akka.persistence.journal.host")
    var redisPort = config.getInt("akka.persistence.journal.port")

    redis = RedisClient(
      host = redisHost,
      port = redisPort,
      db = database(config))

    super.preStart()
  }

  override def postStop(): Unit = {
    log.info("[AKKA_PERS] STOPPED InmemJournal")
    redis.stop()
    super.postStop()
  }

  def database(conf: Config): Option[Int] =
    if (conf.hasPath("akka.persistence.journal.db"))
      Some(conf.getInt("akka.persistence.journal.db"))
    else
      None

  def highestSequenceNrKey(persistenceId: String): String =
    "akka:journal:" + persistenceId + ":highestSequenceNr"

  def journalKey(persistenceId: String): String =
    "akka:journal:" + persistenceId

  override def asyncWriteMessages(messages: immutable.Seq[PersistentRepr]): Future[Unit] = {
    // log.info("[AKKA_PERS] STARTED asyncWriteMessages")

    val transaction = redis.transaction()

    val batchOperations = Future
      .sequence {
        messages.map(asyncWriteOperation(transaction, _))
      }
      .map(_ ⇒ ())

    // log.info("[AKKA_PERS] STARTED asyncWriteBatch", batchOperations);

    transaction.exec()

    // log.info("[AKKA_PERS] STOPPED asyncWriteBatch")

    batchOperations
      .map(Success(_))
  }

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(recoveryCallback: PersistentRepr ⇒ Unit): Future[Unit] = {
    // log.info("[AKKA_PERS] STARTED asyncReplayMessages")
    for {
      entries ← redis.zrangebyscore[Journal](journalKey(persistenceId), Limit(fromSequenceNr), Limit(toSequenceNr), Some(0L -> max))
    } yield {
      entries.foreach { entry ⇒
        // log.info("[AKKA_PERS] STARTED replay message")

        try {
          fromBytes[PersistentRepr](entry.persistentRepr) match {
            case Success(pr) ⇒ recoveryCallback(pr)
            case Failure(_)  ⇒ Future.failed(throw new RuntimeException("[AKKA_PERS] asyncReplayMessages: Failed to deserialize PersistentRepr"))
          }
        } catch {
          case ex: Throwable ⇒
            log.info("[AKKA_PERS] replay message ERRROR" + ex)
        }
        ReplaySuccess
      }
    }
  }

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = {
    for (highestStored ← redis.get[Long](highestSequenceNrKey(persistenceId)))
      yield highestStored.getOrElse(fromSequenceNr)
  }

  override def asyncDeleteMessages(messageIds: scala.collection.immutable.Seq[akka.persistence.PersistentId], permanent: Boolean): scala.concurrent.Future[Unit] = {
    // log.info("[AKKA_PERS] STARTED asyncDeleteMessages")
    Future.successful(Nil)
  }
  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long, permanent: Boolean): scala.concurrent.Future[Unit] = {
    // log.info("[AKKA_PERS] STARTED asyncDeleteMessagesTo")
    for {
      _ ← redis.zremrangebyscore(journalKey(persistenceId), Limit(-1), Limit(toSequenceNr))
    } yield ()
  }
  override def asyncWriteConfirmations(confirmations: scala.collection.immutable.Seq[akka.persistence.PersistentConfirmation]): scala.concurrent.Future[Unit] = {
    // log.info("[AKKA_PERS] STARTED asyncWriteConfirmations")
    Future.successful(Nil)
  }

  private def asyncWriteOperation(transaction: TransactionBuilder, pr: PersistentRepr): Future[Unit] = {
    toBytes(pr) match {
      case Success(serialized) ⇒
        val journal = Journal(pr.sequenceNr, serialized, pr.deleted, ZonedDateTime.now(ZoneOffset.UTC))
        transaction
          .zadd(journalKey(pr.persistenceId), (pr.sequenceNr, journal))
          .zip(transaction.set(highestSequenceNrKey(pr.persistenceId), pr.sequenceNr))
          .zip(transaction.sadd(identifiersKey, pr.persistenceId))
          .map(_ ⇒ ())
      case Failure(e) ⇒ Future.failed(new scala.RuntimeException("writeMessages: failed to write PersistentRepr to redis"))
    }
  }

}
