package org.scarlett.actors

import akka.actor.{Actor, ActorLogging, Props, Terminated}
import akka.event.Logging
import akka.io.Tcp._
import akka.io.{IO, Tcp}

import java.net.InetSocketAddress

class Broker(port: Int) extends Actor with ActorLogging {
  import context.system

  IO(Tcp) ! Bind(self, new InetSocketAddress(port))

  override def receive: Receive = init

  private def init: Receive = {
    case Bound(_) =>
      log.info(s"Server bound to $port")

      context.become(handleConnections)

    case CommandFailed(_: Bind) =>
      log.error(s"Cannot bind to $port")
      context.stop(self)
  }

  private def handleConnections: Receive = {
    case Connected(_, _) =>
      sender() ! Register(context.actorOf(Props(classOf[ClientSerializer], sender())))
  }
}
