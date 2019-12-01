package com.example.fsm

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import scala.concurrent.duration._
import scala.collection.immutable

object Buncher {
  // FSM event becomes the type of the message Actor supports
  sealed trait Event
  final case class SetTarget(ref: ActorRef[Batch]) extends Event
  final case class Queue(obj: Any) extends Event
  case object Flush extends Event
  private case object Timeout extends Event

  sealed trait Data
  case object Uninitialized extends Data
  final case class Todo(target: ActorRef[Batch], queue: immutable.Seq[Any]) extends Data

  final case class Batch(obj: immutable.Seq[Any])

  // initial state
  def apply(): Behavior[Event] = idle(Uninitialized)

  private def idle(data: Data): Behavior[Event] = Behaviors.receiveMessage[Event] { message: Event =>
    (message, data) match {
      case (SetTarget(ref), Uninitialized) =>
        idle(Todo(ref, Vector.empty))
      case (Queue(obj), t @ Todo(_, v)) =>
        active(t.copy(queue = v :+ obj))
      case _ =>
        Behaviors.unhandled
    }
  }

  private def active(data: Todo): Behavior[Event] =
    Behaviors.withTimers[Event] { timers =>
      // instead of FSM state timeout
      timers.startSingleTimer(Timeout, Timeout, 1.second)
      Behaviors.receiveMessagePartial {
        case Flush | Timeout =>
          data.target ! Batch(data.queue)
          idle(data.copy(queue = Vector.empty))
        case Queue(obj) =>
          active(data.copy(queue = data.queue :+ obj))
      }
    }

}
