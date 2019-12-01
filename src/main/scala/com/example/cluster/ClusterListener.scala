package com.example.cluster

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ClusterEvent.{ClusterDomainEvent, LeaderChanged, MemberDowned, MemberEvent, MemberRemoved, MemberUp, ReachabilityEvent, ReachableMember, RoleLeaderChanged, UnreachableMember}
import akka.cluster.typed.Cluster
import akka.cluster.typed.Subscribe

object ClusterListener {

  sealed trait Event

  def apply(): Behavior[ClusterDomainEvent] = Behaviors.setup { ctx =>
    Cluster(ctx.system).subscriptions ! Subscribe(ctx.self, classOf[ClusterDomainEvent])


    Behaviors.receiveMessage { message =>
      message match {
        case memberEvent: MemberEvent =>
          ctx.log.info("成员：{}，状态：{}",memberEvent.member.address,memberEvent.member.status)
        case leaderChanged: LeaderChanged =>
          ctx.log.info("当前的集群leader是：{}",leaderChanged.leader)
        case roleLeaderChanged: RoleLeaderChanged =>
          ctx.log.info("当前的RoleLeader是：{}",roleLeaderChanged)
      }
      Behaviors.same
    }
  }
}