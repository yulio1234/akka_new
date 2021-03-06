package com.example.persistence

import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}

/**
 * 基于领域驱动设计历练的账号实体，采用event sourcing方案
 * 将事件处理委托给账号实体
 */
object AccountEntity1 {
  // Command
  sealed trait Command[Reply <: CommandReply] extends CborSerializable {
    def replyTo: ActorRef[Reply]
  }
  final case class CreateAccount(replyTo: ActorRef[OperationResult]) extends Command[OperationResult]
  final case class Deposit(amount: BigDecimal, replyTo: ActorRef[OperationResult]) extends Command[OperationResult]
  final case class Withdraw(amount: BigDecimal, replyTo: ActorRef[OperationResult]) extends Command[OperationResult]
  final case class GetBalance(replyTo: ActorRef[CurrentBalance]) extends Command[CurrentBalance]
  final case class CloseAccount(replyTo: ActorRef[OperationResult]) extends Command[OperationResult]

  // Reply
  sealed trait CommandReply extends CborSerializable
  sealed trait OperationResult extends CommandReply
  case object Confirmed extends OperationResult
  final case class Rejected(reason: String) extends OperationResult
  final case class CurrentBalance(balance: BigDecimal) extends CommandReply

  // Event
  sealed trait Event extends CborSerializable
  case object AccountCreated extends Event
  case class Deposited(amount: BigDecimal) extends Event
  case class Withdrawn(amount: BigDecimal) extends Event
  case object AccountClosed extends Event

  val Zero = BigDecimal(0)

  // State
  sealed trait Account extends CborSerializable {
    def applyEvent(event: Event): Account
  }
  case object EmptyAccount extends Account {
    override def applyEvent(event: Event): Account = event match {
      case AccountCreated => OpenedAccount(Zero)
      case _              => throw new IllegalStateException(s"unexpected event [$event] in state [EmptyAccount]")
    }
  }
  case class OpenedAccount(balance: BigDecimal) extends Account {
    require(balance >= Zero, "Account balance can't be negative")

    override def applyEvent(event: Event): Account =
      event match {
        case Deposited(amount) => copy(balance = balance + amount)
        case Withdrawn(amount) => copy(balance = balance - amount)
        case AccountClosed     => ClosedAccount
        case AccountCreated    => throw new IllegalStateException(s"unexpected event [$event] in state [OpenedAccount]")
      }

    def canWithdraw(amount: BigDecimal): Boolean = {
      balance - amount >= Zero
    }

  }
  case object ClosedAccount extends Account {
    override def applyEvent(event: Event): Account =
      throw new IllegalStateException(s"unexpected event [$event] in state [ClosedAccount]")
  }

  val TypeKey: EntityTypeKey[Command[_]] =
    EntityTypeKey[Command[_]]("Account")

  // Note that after defining command, event and state classes you would probably start here when writing this.
  // When filling in the parameters of EventSourcedBehavior.apply you can use IntelliJ alt+Enter > createValue
  // to generate the stub with types for the command and event handlers.

  def apply(accountNumber: String, persistenceId: PersistenceId): Behavior[Command[_]] = {
    EventSourcedBehavior.withEnforcedReplies(persistenceId, EmptyAccount, commandHandler(accountNumber), eventHandler)
  }

  private def commandHandler(accountNumber: String): (Account, Command[_]) => ReplyEffect[Event, Account] = {
    (state, cmd) =>
      state match {
        case EmptyAccount =>
          cmd match {
            case c: CreateAccount => createAccount(c)
            case _                => Effect.unhandled.thenNoReply() // CreateAccount before handling any other commands
          }

        case acc @ OpenedAccount(_) =>
          cmd match {
            case c: Deposit       => deposit(c)
            case c: Withdraw      => withdraw(acc, c)
            case c: GetBalance    => getBalance(acc, c)
            case c: CloseAccount  => closeAccount(acc, c)
            case c: CreateAccount => Effect.reply(c.replyTo)(Rejected(s"Account $accountNumber is already created"))
          }

        case ClosedAccount =>
          cmd match {
            case Deposit(_,replyTo) =>
              Effect.reply(replyTo)(Rejected("Account is closed"))
            case Withdraw(_,replyTo) =>
              Effect.reply(replyTo)(Rejected("Account is closed"))
            case GetBalance(replyTo) =>
              Effect.reply(replyTo)(CurrentBalance(Zero))
            case CloseAccount(replyTo) =>
              Effect.reply(replyTo)(Rejected(s"Account $accountNumber is already closed"))
            case CreateAccount(replyTo) =>
              Effect.reply(replyTo)(Rejected(s"Account $accountNumber is already closed"))
          }
      }
  }

  private val eventHandler: (Account, Event) => Account = { (state, event) =>
    state.applyEvent(event)
  }

  private def createAccount(cmd: CreateAccount): ReplyEffect[Event, Account] = {
    Effect.persist(AccountCreated).thenReply(cmd.replyTo)(_ => Confirmed)
  }

  private def deposit(cmd: Deposit): ReplyEffect[Event, Account] = {
    Effect.persist(Deposited(cmd.amount)).thenReply(cmd.replyTo)(_ => Confirmed)
  }

  private def withdraw(acc: OpenedAccount, cmd: Withdraw): ReplyEffect[Event, Account] = {
    if (acc.canWithdraw(cmd.amount))
      Effect.persist(Withdrawn(cmd.amount)).thenReply(cmd.replyTo)(_ => Confirmed)
    else
      Effect.reply(cmd.replyTo)(Rejected(s"Insufficient balance ${acc.balance} to be able to withdraw ${cmd.amount}"))
  }

  private def getBalance(acc: OpenedAccount, cmd: GetBalance): ReplyEffect[Event, Account] = {
    Effect.reply(cmd.replyTo)(CurrentBalance(acc.balance))
  }

  private def closeAccount(acc: OpenedAccount, cmd: CloseAccount): ReplyEffect[Event, Account] = {
    if (acc.balance == Zero)
      Effect.persist(AccountClosed).thenReply(cmd.replyTo)(_ => Confirmed)
    else
      Effect.reply(cmd.replyTo)(Rejected("Can't close account with non-zero balance"))
  }

}