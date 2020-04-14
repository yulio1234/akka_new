package com.example.first

import akka.actor.typed.ActorRef

/**
 * 通讯协议
 */
object Protocol {
  //定义招呼请求事件
  final case class Greet(whom: String, replyTo: ActorRef[Greeted])
  //定义招呼响应事件
  final case class Greeted(whom: String, from: ActorRef[Greet])
}
