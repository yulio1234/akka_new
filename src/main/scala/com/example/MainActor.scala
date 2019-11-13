package com.example

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}

object MainActor {
  def apply(): Behavior[String] =
    Behaviors.setup(context => new MainActor(context))

}

class MainActor(context: ActorContext[String]) extends AbstractBehavior[String](context) {
  override def onMessage(msg: String): Behavior[String] =
    msg match {
      case "start" =>
        val firstRef = context.spawn(PrintMyActorRefActor(), "first-actor")
        println(s"First: $firstRef")
        firstRef ! "printit"
        this
      case "stop" =>{
        val stopRef = context.spawn(StartStopActor1(),"start-stop-actor")
        stopRef ! "stop"
        this
      }
      case "fail" =>
        val supervisingActor = context.spawn(SupervisingActor(), "supervising-actor")
        supervisingActor ! "failChild"
        this
    }
}
