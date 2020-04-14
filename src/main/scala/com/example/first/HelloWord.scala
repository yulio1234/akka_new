package com.example.first

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import com.example.first.Protocol.{Greet, Greeted}
object HelloWord{
  def apply(myself:String): Behavior[Protocol.Greet] = Behaviors.setup(context => new HelloWord(myself,context))
}
class HelloWord(myself:String,context: ActorContext[Greet]) extends AbstractBehavior[Greet](context){
  /**
   * 消息唯一入口，并且时顺序的。
   * @param msg
   * @return
   */
  override def onMessage(msg: Greet): Behavior[Greet] = msg match {
    case Greet(whom, replyTo) =>{
      context.log.info("Hello {}!", whom)
      replyTo ! Greeted(myself, context.self)
      this
    }
  }
}
