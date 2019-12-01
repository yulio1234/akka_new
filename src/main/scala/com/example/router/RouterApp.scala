package com.example.router

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.example.cluster.ClusterListener

class RouterApp {
  object RootBehavior {
    def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { context =>
      // Create an actor that handles cluster domain events

      Behaviors.empty
    }
  }
  def main(args: Array[String]): Unit = {
    ActorSystem[Nothing](RootBehavior(), "RouterSystem")

  }

}
