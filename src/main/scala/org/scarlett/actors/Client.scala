package org.scarlett.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Timers}
import org.scarlett.codecs.QOS.QOS
import org.scarlett.codecs._
import scala.collection._

import java.time.Duration

class Client(private val serializer: ActorRef) extends Actor with ActorLogging with Timers {
  case object KeepAlive

  private val publisher = context
    .actorSelection("/user/publisher")
    .resolveOne(Duration.ofSeconds(1))
    .toCompletableFuture
    .get()

  override def receive: Receive = init

  private def init: Receive = {
    case Frame(_, c: Connect) =>
      (c.header.cleanSession, c.header.keepAliveTime) match {
        case (false, _) =>
          serializer ! Frame.frameForPacket(ConnAck(sessionPresent = false, ReturnCode.IDENTIFIER_REJECTED))
          context.stop(self)
        case (_, 0) =>
          serializer ! Frame.frameForPacket(ConnAck(sessionPresent = false, ReturnCode.CONNECTION_ACCEPTED))
          context.become(new RunningClient(None, c.clientId).running)
        case (_, keepAlive) =>
          val dur: Duration = Duration.ofSeconds((keepAlive * 1.5).toLong)

          serializer ! Frame.frameForPacket(ConnAck(sessionPresent = false, ReturnCode.CONNECTION_ACCEPTED))
          context.become(new RunningClient(Some(dur), c.clientId).running)

          timers.startSingleTimer(KeepAlive, KeepAlive, dur)
      }

    case _ =>
      context.stop(self)
  }

  class RunningClient(private val keepAliveDuration: Option[Duration], private val clientId: String) {
    private val inflight: mutable.Map[Int, QOS] = mutable.Map.empty

    def running: Receive = new PartialFunction[Any, Unit] {
      def apply(x: Any): Unit = {
        timers.cancel(KeepAlive)

        handleMessage(x)

        keepAliveDuration.foreach(dur =>
          timers.startSingleTimer(KeepAlive, KeepAlive, dur)
        )
      }

      def isDefinedAt(x: Any): Boolean = handleMessage.isDefinedAt(x)
    }

    val handleMessage: Receive = {
      case KeepAlive => die()
      case Frame(header, packet) => packet match {
        case _: Connect => die()
        case _: ConnAck => die()
        case _: SubAck => die()
        case _: UnsubAck => die()
        case PingResp => die()
        case Disconnect =>
          die()
        case Publish(_, id, _) =>
          if (sender() == serializer) {
            publisher ! Frame(header, packet)

            if (header.qos != QOS.QOS0) {
              serializer ! Frame.frameForPacket(PubAck(id.get))
            }
          }
          else {
            if (header.qos != QOS.QOS0) {
              inflight += (id.get -> header.qos)
            }

            serializer ! Frame(Header(header.messageType, false, header.qos, header.retain), packet)
          }
        case PubAck(id) =>
          if (inflight(id) == QOS.QOS2) {
            serializer ! Frame.frameForPacket(PubRec(id))
          }
          inflight -= id
        case PubRec(id) =>
          serializer ! Frame.frameForPacket(PubRel(id))
        case PubRel(id) =>
          serializer ! Frame.frameForPacket(PubComp(id))
        case PubComp(id) =>
          ()
        case Subscribe(id, subscription) =>
          val validTopics = subscription
            .filter(topicSub => Publisher.isTopicValid(topicSub.topic))

          publisher ! Frame.frameForPacket(Subscribe(id, validTopics))

          serializer ! Frame.frameForPacket(SubAck(id, subscription.map {
            case TopicSubscription(topic, _) if !Publisher.isTopicValid(topic) => SubscriptionReturnCode.FAILURE
            case TopicSubscription(_, QOS.QOS0) => SubscriptionReturnCode.OK_QOS0
            case TopicSubscription(_, QOS.QOS1) => SubscriptionReturnCode.OK_QOS1
            case TopicSubscription(_, QOS.QOS2) => SubscriptionReturnCode.OK_QOS2
            case _ => throw new RuntimeException()
          }))
        case u @ Unsubscribe(id, _) => {
          publisher ! Frame.frameForPacket(u)

          serializer ! Frame.frameForPacket(UnsubAck(id))
        }
        case PingReq => serializer ! Frame.frameForPacket(PingResp)
      }
    }

    private def die(): Unit = context.stop(self)
  }

  override def postStop(): Unit = {
    publisher ! Publisher.UnsubscribeAll
  }
}
