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
  val orderType = "com.example.Order"

  val model = DomainModel("OrderProcessing")

  model.registerAggregateType(orderType)

  val order = model.aggregateOf(orderType, "123")

  order ! InitializeOrder(249.95)

  order ! ProcessOrder()

  awaitCompletion

  model.shutdown()

  println("DomainModelPrototype: is completed.")
}

object DomainModel {
  def apply(name: String): DomainModel = {
    new DomainModel(name)
  }
}

class DomainModel(name: String) {
  val aggregateTypeRegistry = scala.collection.mutable.Map[String, AggregateType]()
  val system = ActorSystem(name)

  def aggregateOf(typeName: String, id: String): AggregateRef = {
    if (aggregateTypeRegistry.contains(typeName)) {
      val aggregateType = aggregateTypeRegistry(typeName)
      aggregateType.cacheActor ! RegisterAggregateId(id)
      AggregateRef(id, aggregateType.cacheActor)
    } else {
      throw new IllegalStateException(s"DomainModel type registry does not have a $typeName")
    }
  }

  def registerAggregateType(typeName: String): Unit = {
    if (!aggregateTypeRegistry.contains(typeName)) {
      val actorRef = system.actorOf(Props(classOf[AggregateCache], typeName), typeName)
      aggregateTypeRegistry(typeName) = AggregateType(actorRef)
    }
  }

  def shutdown() = {
    system.shutdown()
  }
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