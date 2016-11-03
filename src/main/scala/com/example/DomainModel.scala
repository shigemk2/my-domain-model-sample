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
