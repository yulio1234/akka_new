package com.example.first

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import com.example.first.Protocol.{Greet, Greeted}
object HelloWorldBot{
  def apply(myself:String): Behavior[Protocol.Greeted] = Behaviors.setup(context => new HelloWorldBot(myself,context))
}
class HelloWorldBot(myself:String,context: ActorContext[Greeted]) extends AbstractBehavior[Greeted](context) {
  override def onMessage(msg: Greeted): Behavior[Greeted] = {
    context.log.info("Hello {}!", msg.whom)
    msg.from ! Greet(myself,context.self)
    this
  }
}
