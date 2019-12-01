package com.example.cluster

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object ClusterListenerApp {
  object RootBehavior {
    def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { context =>
      // Create an actor that handles cluster domain events
      context.spawn(ClusterListener(), "ClusterListener")

      Behaviors.empty
    }
  }
  def main(args: Array[String]): Unit = {
    var root = ActorSystem[Nothing](RootBehavior(), "ClusterSystem")
  }
}
