package com.example.persistence

import akka.actor.typed.{ActorSystem, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

/**
 * 1：基础的例子，构建基本的event sourcing
 */
object MyPersistentBehavior {
  sealed trait Command
  sealed trait Event

  final case class Add(data:String) extends Command
  case object Clear extends Command
  final case class Added(data:String) extends Event
  case object Cleared extends Event
  final case class State(history:List[String] = Nil)

  def apply(id:String): Behavior[Command] =
    EventSourcedBehavior[Command, Event, State](
      persistenceId = PersistenceId.ofUniqueId(id),
      emptyState = State(Nil),
      commandHandler = commandHandler,
      eventHandler = eventHandler)

  /**
   * 命令处理器，负责处理各种命令，然后转换成事件存储，
   */
  val commandHandler:(State,Command) => Effect[Event,State] = { (state,command) =>
    command match {
      /**
       * 存储后可以执行其他操作
       */
      case Add(data) => Effect.persist(Added(data)).thenRun(newState => println(newState.history))
      case Clear => Effect.persist(Cleared).thenRun((state:State) => println(state)).thenStop()
    }
  }
  /**
   * 存储数据后执行的事件，通过事件来修改状态
   */
  val eventHandler:(State,Event) => State = {(state,event) =>
    event match  {
      case Added(data) => state.copy((data:: state.history).take(5))
      case Cleared => State(Nil)
    }

  }

  def main(args: Array[String]): Unit = {
    val system = ActorSystem(MyPersistentBehavior("1"),"test")
    system ! Add("hello")
    Thread.sleep(19099)
  }
}
