package com.example.cluster

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.Entity
import akka.persistence.typed.PersistenceId
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Future

class HelloWorldService(system: ActorSystem[_]) {
  import system.executionContext

  private val sharding = ClusterSharding(system)

  // registration at startup
  sharding.init(Entity(typeKey = HelloWorld.TypeKey) { entityContext =>
    HelloWorld(entityContext.entityId, PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId))
  })

  private implicit val askTimeout: Timeout = Timeout(5.seconds)

  def greet(worldId: String, whom: String): Future[Int] = {
    val entityRef = sharding.entityRefFor(HelloWorld.TypeKey, worldId)
    val greeting = entityRef ? HelloWorld.Greet(whom)
    greeting.map(_.numberOfPeople)
  }

}