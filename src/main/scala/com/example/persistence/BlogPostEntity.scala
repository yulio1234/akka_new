package com.example.persistence

import akka.Done
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

/**
 * 2：一个博客的例子，构建基于状态机的event sourcing
 */
class BlogPostEntity {
  sealed trait State

  case object BlankState extends State

  final case class DraftState(content: PostContent) extends State {
    def withBody(newBody: String): DraftState =
      copy(content = content.copy(body = newBody))

    def postId: String = content.postId
  }

  final case class PublishedState(content: PostContent) extends State {
    def postId: String = content.postId
  }
  sealed trait Command
  final case class AddPost(content: PostContent, replyTo: ActorRef[AddPostDone]) extends Command
  final case class AddPostDone(postId: String)
  final case class GetPost(replyTo: ActorRef[PostContent]) extends Command
  final case class ChangeBody(newBody: String, replyTo: ActorRef[Done]) extends Command
  final case class Publish(replyTo: ActorRef[Done]) extends Command
  final case class PostContent(postId: String, title: String, body: String)


  sealed trait Event
  final case class PostAdded(postId: String, content: BlogPostEntity.this.PostContent) extends Event
  final case class BodyChanged(str: String, str1: String) extends Event
  final case class Published(str: String) extends Event

  /**
   * 命令处理器，因为es不能返回behavior，通过匹配状态类来定义状态机，不同的状态类处理不同的事件
   */
  private val commandHandler: (State, Command) => Effect[Event, State] = { (state, command) =>
    state match {

      case BlankState =>
        command match {
          case cmd: AddPost => addPost(cmd)
          case _            => Effect.unhandled
        }

      case draftState: DraftState =>
        command match {
          case cmd: ChangeBody  => changeBody(draftState, cmd)
          case Publish(replyTo) => publish(draftState, replyTo)
          case GetPost(replyTo) => getPost(draftState, replyTo)
          case _: AddPost       => Effect.unhandled
        }

      case publishedState: PublishedState =>
        command match {
          case GetPost(replyTo) => getPost(publishedState, replyTo)
          case _                => Effect.unhandled
        }
    }
  }

  private def addPost(cmd: AddPost): Effect[Event, State] = {
    val evt = PostAdded(cmd.content.postId, cmd.content)
    Effect.persist(evt).thenRun { _ =>
      // After persist is done additional side effects can be performed
      cmd.replyTo ! AddPostDone(cmd.content.postId)
    }
  }

  private def changeBody(state: DraftState, cmd: ChangeBody): Effect[Event, State] = {
    val evt = BodyChanged(state.postId, cmd.newBody)
    Effect.persist(evt).thenRun { _ =>
      cmd.replyTo ! Done
    }
  }

  private def publish(state: DraftState, replyTo: ActorRef[Done]): Effect[Event, State] = {
    Effect.persist(Published(state.postId)).thenRun { _ =>
      println(s"Blog post ${state.postId} was published")
      replyTo ! Done
    }
  }

  private def getPost(state: DraftState, replyTo: ActorRef[PostContent]): Effect[Event, State] = {
    replyTo ! state.content
    Effect.none
  }

  private def getPost(state: PublishedState, replyTo: ActorRef[PostContent]): Effect[Event, State] = {
    replyTo ! state.content
    Effect.none
  }

  /**
   * 事件处理器，触发事件后，改变状态类，达到状态机的目的
   */
  private val eventHandler: (State, Event) => State = { (state, event) =>
    state match {

      case BlankState =>
        event match {
          case PostAdded(_, content) =>
            DraftState(content)
          case _ => throw new IllegalStateException(s"unexpected event [$event] in state [$state]")
        }

      case draftState: DraftState =>
        event match {

          case BodyChanged(_, newBody) =>
            draftState.withBody(newBody)

          case Published(_) =>
            PublishedState(draftState.content)

          case _ => throw new IllegalStateException(s"unexpected event [$event] in state [$state]")
        }

      case _: PublishedState =>
        // no more changes after published
        throw new IllegalStateException(s"unexpected event [$event] in state [$state]")
    }
  }

  def apply(entityId: String, persistenceId: PersistenceId): Behavior[Command] = {
    Behaviors.setup { context =>
      context.log.info("Starting BlogPostEntity {}", entityId)
      EventSourcedBehavior[Command, Event, State](persistenceId, emptyState = BlankState, commandHandler, eventHandler)
    }
  }
}
