package com.example.fsm

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.example.fsm.Buncher.{Batch, Queue, SetTarget}
import org.scalatest.WordSpecLike

class FsmSpec extends ScalaTestWithActorTestKit with WordSpecLike {
  "test fsm" in {
    val actorRef = createTestProbe[Batch]()
    val buncher = spawn(Buncher())
    buncher ! SetTarget(actorRef.ref)
    (1 to 100).foreach(buncher ! Queue(_))
    val message = actorRef.receiveMessage()
    message.obj.foreach(println)
  }

}
