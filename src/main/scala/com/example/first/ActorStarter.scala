package com.example.first

import akka.actor.typed.ActorSystem
import com.example.first.Protocol.Greet

object ActorStarter extends App {
  val testSystem1 = ActorSystem(HelloWord("小明"), "testSystem1")
  val testSystem2 = ActorSystem(HelloWorldBot("小花"), "testSystem2")

  testSystem1 ! Greet("你好",testSystem2)

  testSystem1.terminate()
}
