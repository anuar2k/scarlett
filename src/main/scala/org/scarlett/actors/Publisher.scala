package org.scarlett.actors

import akka.actor.{Actor, ActorLogging, ActorRef}
import org.scarlett.codecs.QOS.QOS
import org.scarlett.codecs.{Frame, Header, Publish, QOS, Subscribe, TopicSubscription, Unsubscribe}

import scala.collection.mutable
import scala.math.Ordering.Implicits.infixOrderingOps

class Publisher extends Actor with ActorLogging {
  private var topics: mutable.Map[ActorRef, mutable.Set[TopicSubscription]] =
    mutable.Map.empty

  def doTopicsMatch(
      publishedTopic: String,
      subscribedTopic: String
  ): Boolean = {
    publishedTopic
      .split("/")
      .zipAll(subscribedTopic.split("/"), "$", "$")
      .takeWhile { case (_, sub) => sub != "#" }
      .foldLeft(true) {
        case (acc, (pub, sub)) =>
          sub match {
            case "+"             => acc
            case _ if pub == sub => acc
            case _               => false
          }
      }
  }

  def dispatchMessage(qos: QOS, packet: Publish): Unit = {
    val topic = packet.topic
    for ((subscriber, topicList) <- topics) {
      topicList
        .filter(topicSub => doTopicsMatch(topic, topicSub.topic))
        .foreach(
          topicSub => subscriber ! Frame(Header(3, false, qos.min(topicSub.qos), false), packet)
        )
    }
  }

  def unsubscribe(
      unsubscribed_topics: Set[String],
      subscriber: ActorRef
  ): Unit = {
    if (topics.contains(subscriber)) {
      topics(subscriber) --= topics(subscriber).filter {
        case TopicSubscription(topic, _qos) =>
          unsubscribed_topics.contains(topic)
      }
    }
  }

  def unsubscribeAll(subscriber: ActorRef): Unit = {
    topics -= subscriber
  }

  def subscribe(
      subscribed_topics: Set[TopicSubscription],
      subscriber: ActorRef
  ): Unit = {
    if (topics.contains(subscriber)) {
      // update QOS
      topics(subscriber) --= subscribed_topics
      topics(subscriber) ++= subscribed_topics
    } else {
      topics(subscriber) = mutable.Set.from(subscribed_topics)
    }
  }

  def receive: Receive = {
    case Frame(_, Subscribe(_id, topics)) =>
      subscribe(Set.from(topics), sender())
    case Frame(_, Unsubscribe(_id, topics)) =>
      unsubscribe(Set.from(topics), sender())
    case Frame(header, packet @ Publish(topic, _, _)) =>
      dispatchMessage(header.qos, packet)
    case Publisher.UnsubscribeAll => unsubscribeAll(sender())
  }
}

object Publisher {
  case object UnsubscribeAll

  def isTopicValid(topic: String, isTopicFilter: Boolean = true): Boolean = {
    def isLongEnough: Boolean = {
      topic.nonEmpty
    }
    def validHashPosition: Boolean = {
      !topic.contains("#") || (!topic.dropRight(1).contains("#") && topic
        .endsWith("#"))
    }
    def notStartsWithDollarChar: Boolean = {
      !topic.startsWith("$")
    }
    def validPlusWildcard: Boolean = {
      topic.split("/").filter(_.contains("+")).forall(_ == "+")
    }
    def containsValidChars: Boolean = {
      topic.toList.forall(!List("#", "+").contains(_))
    }

    if (isTopicFilter)
      isLongEnough && validHashPosition && validPlusWildcard && notStartsWithDollarChar
    else
      isLongEnough && containsValidChars
  }
}
