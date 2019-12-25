package com.example.sharding

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef, EntityTypeKey}

object Counter {
  sealed trait Command
  case object Increment extends Command
  final case class GetValue(replyTo: ActorRef[Int]) extends Command

  def apply(entityId: String): Behavior[Command] = {
    def updated(value: Int): Behavior[Command] = {
      Behaviors.receiveMessage[Command] {
        case Increment =>
          updated(value + 1)
        case GetValue(replyTo) =>
          replyTo ! value
          Behaviors.same
      }
    }

    updated(0)
  }

  def main(args: Array[String]): Unit = {
    val system = ActorSystem[Command](Counter("counter"), "test")
    val sharding = ClusterSharding(system)
    val TypeKey = EntityTypeKey[Command]("Counter")
    val shardRegion:ActorRef[ShardingEnvelope[Command]] = sharding.init(Entity(TypeKey)(createBehavior = entityContext => Counter(entityContext.entityId)))
    val counterOne:EntityRef[Command] = sharding.entityRefFor(TypeKey, "counter-1")
//    sharding.counterOne ! Counter.Increment
  }
}
