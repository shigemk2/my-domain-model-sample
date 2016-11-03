package com.example

import akka.actor.Actor.Receive
import akka.actor._

class Order extends Actor {
  var amount: Double = _

  override def receive: Receive = {
    case init: InitializeOrder =>
      println(s"Initializing Order with $init")
      this.amount = init.amount
    case processOrder: ProcessOrder =>
      println(s"Processing Order is $processOrder")
      DomainModelPrototype.completedStep()
  }
}

case class InitializeOrder(amount: Double)
case class ProcessOrder()

object DomainModelPrototype extends CompletableApp(1) {
}

class AggregateCache(typeName: String) extends Actor {
  val aggregateClass: Class[Actor] = Class.forName(typeName).asInstanceOf[Class[Actor]]
  val aggregateIds = scala.collection.mutable.Set[String]()

  override def receive: Receive = {
    case message: CacheMessage =>
      val aggregate = context.child(message.id).getOrElse {
        if (!aggregateIds.contains(message.id)) {
          throw new IllegalStateException(s"No aggregate of type $typeName and id ${message.id}")
        } else {
          context.actorOf(Props(aggregateClass), message.id)
        }
      }
      aggregate.tell(message.actualMessage, message.sender)

    case register: RegisterAggregateId =>
      this.aggregateIds.add(register.id)
  }
}

case class AggregateRef(id: String, cache: ActorRef) {
  def tell(message: Any)(implicit sender: ActorRef = null): Unit = {
    cache ! CacheMessage(id, message, sender)
  }

  def !(message: Any)(implicit sender: ActorRef = null): Unit = {
    cache ! CacheMessage(id, message, sender)
  }
}

case class AggregateType(cacheActor: ActorRef)

case class CacheMessage(id: String, actualMessage: Any, sender: ActorRef)

case class RegisterAggregateId(id: String)