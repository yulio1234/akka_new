package com.example.learn

import java.net.URI

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object Backend {
  sealed trait Request
  final case class StartTranslationJob(taskId: Int, site: URI, replyTo: ActorRef[Response]) extends Request

  sealed trait Response
  final case class JobStarted(taskId: Int) extends Response
  final case class JobProgress(taskId: Int, progress: Double) extends Response
  final case class JobCompleted(taskId: Int, result: URI) extends Response
}

object Frontend {

  sealed trait Command
  final case class Translate(site: URI, replyTo: ActorRef[URI]) extends Command
  private final case class WrappedBackendResponse(response: Backend.Response) extends Command

  def apply(backend: ActorRef[Backend.Request]): Behavior[Command] =
    Behaviors.setup[Command] { context =>
      val backendResponseMapper: ActorRef[Backend.Response] = context.messageAdapter(rsp => WrappedBackendResponse(rsp))

      def active(inProgress: Map[Int, ActorRef[URI]], count: Int): Behavior[Command] = {
        Behaviors.receiveMessage[Command] {
          case Translate(site, replyTo) =>
            val taskId = count + 1
            backend ! Backend.StartTranslationJob(taskId, site, backendResponseMapper)
            active(inProgress.updated(taskId, replyTo), taskId)

          case wrapped: WrappedBackendResponse =>
            wrapped.response match {
              case Backend.JobStarted(taskId) =>
                context.log.info("Started {}", taskId)
                Behaviors.same
              case Backend.JobProgress(taskId, progress) =>
                context.log.info("Progress {}: {}", taskId, progress)
                Behaviors.same
              case Backend.JobCompleted(taskId, result) =>
                context.log.info("Completed {}: {}", taskId, result)
                inProgress(taskId) ! result
                active(inProgress - taskId, count)
            }
        }
      }

      active(inProgress = Map.empty, count = 0)
    }
}