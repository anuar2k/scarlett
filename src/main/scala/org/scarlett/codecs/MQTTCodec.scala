package org.scarlett.codecs

import org.scarlett.codecs
import scodec._
import scodec.bits._

object MQTTCodec extends Codec[Frame] {
  import QOS._
  import ReturnCode._
  import SubscriptionReturnCode._
  import scodec.codecs._

  val str: Codec[String] = variableSizeBytes(uint16, utf8).as[String]

  val qos: DiscriminatorCodec[codecs.QOS.Value, Int] =
    mappedEnum(uint(2), QOS0 -> 0x00, QOS1 -> 0x01, QOS2 -> 0x02)

  val connectHeader: Codec[ConnectHeader] =
    (("MQTT" | constant(hex"00044D515454")) ::
      ("Protocol level" | uint8) ::
      ("User Name Flag" | bool) ::
      ("Password Flag" | bool) ::
      ("Will Retain" | bool) ::
      ("Will QoS" | qos) ::
      ("Will Flag" | bool) ::
      ("Clean Session" | bool) ::
      ("Reserved" | ignore(1)) ::
      ("Keep Alive" | uint16)).dropUnits.as[ConnectHeader]

  val connect: Codec[Connect] = connectHeader
    .flatPrepend(header =>
      ("Client Identifier" | str) ::
        ("Will Topic" | conditional(header.willFlag, str)) ::
        ("Will Message" | conditional(
          header.willFlag,
          variableSizeBytes(uint16, bytes)
        )) ::
        ("User Name" | conditional(header.usernameFlag, str)) ::
        ("Password" | conditional(header.passwordFlag, str))
    )
    .as[Connect]

  val returnCode: DiscriminatorCodec[codecs.ReturnCode.Value, Int] = mappedEnum(
    uint8,
    CONNECTION_ACCEPTED -> 0,
    UNACCEPTABLE_PROTOCOL_VERSION -> 1,
    IDENTIFIER_REJECTED -> 2,
    SERVER_UNAVAILABLE -> 3,
    BAD_AUTHENTICATION -> 4,
    NOT_AUTHORIZED -> 5
  )

  val connack: Codec[ConnAck] = (("Reserved" | ignore(7)) ::
    ("Session Present Flag" | bool(1)) ::
    returnCode).dropUnits.as[ConnAck]

  val header: Codec[Header] = (("MQTT Control Packet type" | uint(4)) ::
    ("DUP Flag" | bool) ::
    ("QoS" | qos) ::
    ("Retain Flag" | bool))
    .as[Header]

  val frame: Codec[Frame] = header
    .flatPrepend((header: Header) =>
      variableSizeBytes(MQTTVarIntCodec, payloadCodec(header)).hlist
    )
    .as[Frame]

  def publish(header: Header): Codec[Publish] =
    (("Topic Name" | str) ::
      ("Packet Identifier" | conditional(header.qos.id > 0, uint16)) ::
      ("Payload" | bytes)).as[Publish]
  val puback: Codec[PubAck] = (("Packet Identifier" | uint16)).as[PubAck]
  val pubrec: Codec[PubRec] = (("Packet Identifier" | uint16)).as[PubRec]
  val pubrel: Codec[PubRel] = (("Packet Identifier" | uint16)).as[PubRel]
  val pubcomp: Codec[PubComp] = (("Packet Identifier" | uint16)).as[PubComp]

  val topicSubscription: Codec[TopicSubscription] = (("Topic Filter" | str) ::
    ("Reserved" | ignore(6)) ::
    ("QoS" | qos)).dropUnits.as[TopicSubscription]

  val subscribe: Codec[Subscribe] = (("Packet Identifier" | uint16) ::
    list(topicSubscription)).as[Subscribe]

  val subscriptionReturnCode
      : DiscriminatorCodec[codecs.SubscriptionReturnCode.Value, Int] =
    mappedEnum(
      uint8,
      OK_QOS0 -> 0x01,
      OK_QOS1 -> 0x02,
      OK_QOS2 -> 0x03,
      FAILURE -> 0x80
    )

  val suback: Codec[SubAck] = (("Packet Identifier" | uint16) ::
    list(subscriptionReturnCode)).as[SubAck]

  val unsubscribe: Codec[Unsubscribe] = (("Packet Identifier" | uint16) ::
    list(str)).as[Unsubscribe]

  val unsuback: Codec[UnsubAck] = (("Packet Identifier" | uint16)).as[UnsubAck]
  val pingreq: Codec[PingReq.type] = provide(PingReq)
  val pingresp: Codec[PingResp.type] = provide(PingResp)
  val disconnect: Codec[Disconnect.type] = provide(Disconnect)

  def payloadCodec(header: Header): DiscriminatorCodec[MQTTMessage, Int] =
    discriminated[MQTTMessage]
      .by(provide(header.messageType))
      .typecase(1, connect)
      .typecase(2, connack)
      .typecase(3, publish(header))
      .typecase(4, puback)
      .typecase(5, pubrec)
      .typecase(6, pubrel)
      .typecase(7, pubcomp)
      .typecase(8, subscribe)
      .typecase(9, suback)
      .typecase(10, unsubscribe)
      .typecase(11, unsuback)
      .typecase(12, pingreq)
      .typecase(13, pingresp)
      .typecase(14, disconnect)

  def encode(m: Frame): Attempt[BitVector] = frame.encode(m)

  def decode(m: BitVector): Attempt[DecodeResult[Frame]] = frame.decode(m)

  override def sizeBound: SizeBound = SizeBound.unknown
}
