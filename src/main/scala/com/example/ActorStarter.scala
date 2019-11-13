package com.example

import akka.actor.typed.ActorSystem



object ActorStarter extends App {
  val testSystem = ActorSystem(MainActor(), "testSystem")
//  testSystem ! "start"
//  testSystem ! "stop"
  testSystem ! "fail"
  testSystem.terminate()
}
