package org.scarlett.codecs

import QOS._
import ReturnCode._
import SubscriptionReturnCode._
import org.scarlett.codecs.Header.headerForPacket
import scodec.bits._

sealed trait MQTTMessage

case class Header(messageType: Int, dup: Boolean, qos: QOS, retain: Boolean)
case class Frame(header: Header, packet: MQTTMessage)

object Header {
  def headerForPacket(packet: MQTTMessage): Header =
    packet match {
      case _: Connect => Header(1, false, QOS.QOS0, false)
      case _: ConnAck => Header(2, false, QOS.QOS0, false)
      case _: PubAck => Header(4, false, QOS.QOS0, false)
      case _: PubRec => Header(5, false, QOS.QOS0, false)
      case _: PubRel => Header(6, false, QOS.QOS1, false)
      case _: PubComp => Header(7, false, QOS.QOS0, false)
      case _: Subscribe => Header(8, false, QOS.QOS1, false)
      case _: SubAck => Header(9, false, QOS.QOS0, false)
      case _: Unsubscribe => Header(10, false, QOS.QOS1, false)
      case _: UnsubAck => Header(11, false, QOS.QOS0, false)
      case PingReq => Header(12, false, QOS.QOS0, false)
      case PingResp => Header(13, false, QOS.QOS0, false)
      case Disconnect => Header(14, false, QOS.QOS0, false)
      case _ => throw new RuntimeException()
    }
}

object Frame {
  def frameForPacket(packet: MQTTMessage): Frame = Frame(headerForPacket(packet), packet)
}

case class ConnectHeader(
    protocolLevel: Int,
    usernameFlag: Boolean,
    passwordFlag: Boolean,
    willRetain: Boolean,
    willQos: QOS,
    willFlag: Boolean,
    cleanSession: Boolean,
    keepAliveTime: Int
)

case class Connect(
    header: ConnectHeader,
    clientId: String,
    willTopic: Option[String],
    willMessage: Option[ByteVector],
    username: Option[String],
    password: Option[String]
) extends MQTTMessage
case class ConnAck(sessionPresent: Boolean, returnCode: ReturnCode)
    extends MQTTMessage
case class Publish(topic: String, id: Option[Int], payload: ByteVector)
    extends MQTTMessage

case class PubAck(id: Int) extends MQTTMessage
case class PubRec(id: Int) extends MQTTMessage
case class PubRel(id: Int) extends MQTTMessage
case class PubComp(id: Int) extends MQTTMessage
case class TopicSubscription(topic: String, qos: QOS) {
  override def equals(arg: Any): Boolean =
    arg match {
      case TopicSubscription(t, _) => t == topic
      case _                       => false
    }
  override def hashCode(): Int = topic.hashCode
}
case class Subscribe(id: Int, subscription: List[TopicSubscription])
    extends MQTTMessage
case class SubAck(id: Int, returnCodes: List[SubscriptionReturnCode])
    extends MQTTMessage
case class Unsubscribe(id: Int, topics: List[String]) extends MQTTMessage
case class UnsubAck(id: Int) extends MQTTMessage
case object PingReq extends MQTTMessage
case object PingResp extends MQTTMessage
case object Disconnect extends MQTTMessage
