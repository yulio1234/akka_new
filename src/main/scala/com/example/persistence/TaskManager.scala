package com.example.persistence

import akka.actor.typed.{Behavior, SupervisorStrategy}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import scala.concurrent.duration._

/**
 * 一个任务管理器的例子，一次只处理一个任务，多余的任务请求会被存储起来，等任务完成后重放
 */
object TaskManager {

  /**
   * 任务流程的三个阶段
   */
  sealed trait Command
  final case class StartTask(taskId: String) extends Command
  final case class NextStep(taskId: String, instruction: String) extends Command
  final case class EndTask(taskId: String) extends Command

  /**
   * 任务完成后的事件
   */
  sealed trait Event
  final case class TaskStarted(taskId: String) extends Event
  final case class TaskStep(taskId: String, instruction: String) extends Event
  final case class TaskCompleted(taskId: String) extends Event

  /**
   *  但钱执行的任务
   * @param taskIdInProgress
   */
  final case class State(taskIdInProgress: Option[String])

  def apply(persistenceId: PersistenceId): Behavior[Command] =
    EventSourcedBehavior[Command, Event, State](
      persistenceId = persistenceId,
      emptyState = State(None),
      commandHandler = (state, command) => onCommand(state, command),
      eventHandler = (state, event) => applyEvent(state, event))
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(1.second, 30.seconds, 0.2))

  private def onCommand(state: State, command: Command): Effect[Event, State] = {
    state.taskIdInProgress match {
      case None =>
        command match {
          case StartTask(taskId) =>
            Effect.persist(TaskStarted(taskId))
          case _ =>
            Effect.unhandled
        }

      case Some(inProgress) =>
        command match {
          case StartTask(taskId) =>
            if (inProgress == taskId)
              Effect.none // duplicate, already in progress
            else
              // other task in progress, wait with new task until later
              Effect.stash()

          case NextStep(taskId, instruction) =>
            if (inProgress == taskId)
              Effect.persist(TaskStep(taskId, instruction))
            else
              // other task in progress, wait with new task until later
              Effect.stash()

          case EndTask(taskId) =>
            if (inProgress == taskId)
              Effect.persist(TaskCompleted(taskId)).thenUnstashAll() // continue with next task
            else
              // other task in progress, wait with new task until later
              Effect.stash()
        }
    }
  }

  private def applyEvent(state: State, event: Event): State = {
    event match {
      case TaskStarted(taskId) => State(Option(taskId))
      case TaskStep(_, _)      => state
      case TaskCompleted(_)    => State(None)
    }
  }
}