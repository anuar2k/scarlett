package org.scarlett

import akka.actor.{ActorSystem, Props}
import org.scarlett.actors.{Broker, Publisher}

object Main extends App {
  val system = ActorSystem("scarlett")
  system.actorOf(Props[Publisher], "publisher")
  system.actorOf(Props(classOf[Broker], 1883), "broker")
}
