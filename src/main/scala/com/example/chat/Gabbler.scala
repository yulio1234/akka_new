//package com.example.chat
//
//import akka.actor.typed.Behavior
//import akka.actor.typed.scaladsl.Behaviors
//
//object Gabbler {
//  import ChatRoom._
//
//  def apply(): Behavior[SessionEvent] =
//    Behaviors.setup { context =>
//      Behaviors.receiveMessage {
//        case SessionGranted(handle) =>
//          handle ! PostMessage("Hello World!")
//          Behaviors.same
//        case MessagePosted(screenName, message) =>
//          context.log.info("message has been posted by '{}': {}", screenName, message)
//          Behaviors.stopped
//      }
//    }
//}