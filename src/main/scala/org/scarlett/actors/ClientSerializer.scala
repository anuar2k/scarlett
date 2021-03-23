package org.scarlett.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.io.Tcp.{PeerClosed, Received, Write}
import akka.util.ByteString
import org.scarlett.codecs._
import scodec.{Attempt, Err}
import scodec.bits.{BitVector, ByteVector}
import scodec.stream.StreamDecoder

class ClientSerializer(private val socket: ActorRef)
    extends Actor
    with ActorLogging {
  private val client = context.actorOf(Props(classOf[Client], self))
  context.watch(client)

  override def receive: Receive = onMessage(BitVector.empty)

  private def onMessage(remaining: BitVector): Receive = {
    case Received(incoming) =>
      var buffer = remaining ++ BitVector(incoming.toArray)

      var shouldParse = true
      while (shouldParse) {
        MQTTCodec.decode(buffer) match {
          case Attempt.Successful(res) =>
            client ! res.value
            buffer = res.remainder
            log.info(s"incoming: ${res.value}")
          case Attempt.Failure(cause) =>
            shouldParse = false
            cause match {
              case Err.InsufficientBits(_needed, _have, _ctx) =>
              // break loop
              case error => log.error(s"Invalid frame: ${error}")
            }
        }
      }

      context.become(onMessage(buffer))

    case frame: Frame =>
      log.info(s"outcoming: $frame")
      MQTTCodec.encode(frame) match {
        case Attempt.Successful(value) =>
          socket ! Write(ByteString.fromArray(value.toByteArray))
        case Attempt.Failure(cause) =>
          log.error(s"Invalid frame: $cause")
        case _ =>
          log.error("unexpected")
      }

    case PeerClosed =>
      context.stop(client)

    case Terminated(_) =>
      context.stop(self) //that kills the connection too
  }
}
