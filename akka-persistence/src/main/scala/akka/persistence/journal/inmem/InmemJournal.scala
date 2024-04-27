/**
 * Copyright (C) 2009-2014 Typesafe Inc. <http://www.typesafe.com>
 */

package akka.persistence.journal.inmem

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

import com.wmg.dsp.tango.royalties.domain.summary.PayeeAccountSummary;
import com.wmg.dsp.tango.royalties.domain.summary.PayeeAccountSummaryId;

import com.wmg.dsp.platform.persistence.messages.DeleteEntities;
import com.wmg.dsp.platform.persistence.messages.DeleteEntity;
import com.wmg.dsp.platform.persistence.messages.DeletedEntities;
import com.wmg.dsp.platform.persistence.messages.DeletedEntity;
import com.wmg.dsp.platform.persistence.messages.EntitiesMessage;
import com.wmg.dsp.platform.persistence.messages.EntityMessage;
import com.wmg.dsp.platform.persistence.messages.GetEntities;
import com.wmg.dsp.platform.persistence.messages.GetEntity;
import com.wmg.dsp.platform.persistence.messages.SaveEntities;
import com.wmg.dsp.platform.persistence.messages.SaveEntity;
import com.wmg.dsp.platform.persistence.messages.SavedEntitiesMessage;
import com.wmg.dsp.platform.persistence.messages.SavedEntityMessage;

import com.wmg.dsp.platform.persistence.postgres.providers.PostgresProvider;
import com.wmg.dsp.tango.royalties.utils.RPComponents;
import java.util.{ UUID, ArrayList, List }
import scala.collection.JavaConverters._

import com.wmg.dsp.tango.royalties.domain.messages.MessageId;
import com.wmg.dsp.tango.royalties.domain.messages.Message;
import com.wmg.dsp.tango.royalties.dao.messages.MessageRepo;

class InmemJournal extends AsyncWriteJournal with ByteArraySerializer with ActorLogging {
  import AsyncWriteProxy.SetStore

  override implicit lazy val actorSystem = context.system

  implicit def ec = context.system.dispatcher

  implicit val serialization = SerializationExtension(context.system)

  val timeout = Timeout(5 seconds)

  var repo: MessageRepo = _

  override def preStart(): Unit = {
    log.info("[AKKA_PERS] STARTED InmemJournal")

    repo = RPComponents.getMessageRepo();

    println(context.system)

    super.preStart()
  }

  override def postStop(): Unit = {
    log.info("[AKKA_PERS] STOPPED InmemJournal")
    super.postStop()
  }

  override def asyncWriteMessages(messages: immutable.Seq[PersistentRepr]): Future[Unit] = {
    log.info("[AKKA_PERS] STARTED asyncWriteMessages")

    val entities = new ArrayList[Message]()

    val batchPostgresOperations =
      Future
        .sequence {
          messages.map(asyncWriteEntityOperation(entities, _))
        }
        .map(_ ⇒ ())

    try {
      repo.save(entities);
    } catch {
      case e: Exception ⇒ e.printStackTrace()
    }

    log.info("[AKKA_PERS] STOPPED asyncWriteBatch")
    ////////////////

    batchPostgresOperations
      .map(Success(_))
  }

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(recoveryCallback: PersistentRepr ⇒ Unit): Future[Unit] = {
    repo.getAll(persistenceId, fromSequenceNr, toSequenceNr)
      .asScala
      .toList
      .foldLeft(Future.successful(())) { (acc, entry) ⇒
        acc.flatMap { _ ⇒
          try {
            val pr = fromBytes[PersistentRepr](entry.getMessage()).getOrElse(
              throw new RuntimeException("[AKKA_PERS] asyncReplayMessages: Failed to deserialize PersistentRepr"))
            recoveryCallback(pr)
            Future.successful(())
          } catch {
            case ex: Throwable ⇒
              log.info("[AKKA_PERS] replay message ERRROR" + ex)
              Future.failed(ex)
          }
        }
      }
  }

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = {
    val result: Long = repo.getHighestSequenceNrKey(persistenceId, fromSequenceNr)
    Future.successful(result)
  }

  override def asyncDeleteMessages(messageIds: scala.collection.immutable.Seq[akka.persistence.PersistentId], permanent: Boolean): scala.concurrent.Future[Unit] = {
    log.info("[AKKA_PERS] STARTED asyncDeleteMessages")
    Future.successful(Nil)
  }
  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long, permanent: Boolean): scala.concurrent.Future[Unit] = {
    log.info("[AKKA_PERS] STARTED asyncDeleteMessagesTo " + persistenceId + " - " + toSequenceNr.toString);
    repo.delete(persistenceId, toSequenceNr)
    Future.successful(Nil)
  }
  override def asyncWriteConfirmations(confirmations: scala.collection.immutable.Seq[akka.persistence.PersistentConfirmation]): scala.concurrent.Future[Unit] = {
    log.info("[AKKA_PERS] STARTED asyncWriteConfirmations")
    Future.successful(Nil)
  }

  private def asyncWriteEntityOperation(entities: ArrayList[Message], pr: PersistentRepr): Future[Unit] = {
    toBytes(pr) match {
      case Success(serialized) ⇒
        entities
          .add(new Message(new MessageId(pr.persistenceId, 0, pr.sequenceNr, "H"), serialized))
        Future.successful(Nil)
      case Failure(e) ⇒ Future.failed(new scala.RuntimeException("writeMessages: failed to write PersistentRepr to postgres"))
    }
  }
}