package com.example.iot

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}

import scala.concurrent.duration.FiniteDuration

/**
 * 设备组查询器
 */
object DeviceGroupQuery {

  def apply(
             deviceIdToActor: Map[String, ActorRef[Device.Command]],
             requestId: Long,
             requester: ActorRef[DeviceManager.RespondAllTemperatures],
             timeout: FiniteDuration): Behavior[Command] = {
    Behaviors.setup { context =>
      Behaviors.withTimers { timers =>
        new DeviceGroupQuery(deviceIdToActor, requestId, requester, timeout, context, timers)
      }
    }
  }

  trait Command

  private case object CollectionTimeout extends Command

  /**
   * 协议转换，将device协议转换为deviceGroupQuery的协议
   * @param response
   */
  final case class WrappedRespondTemperature(response: Device.RespondTemperature) extends Command

  private final case class DeviceTerminated(deviceId: String) extends Command
}

class DeviceGroupQuery(
                        deviceIdToActor: Map[String, ActorRef[Device.Command]],
                        requestId: Long,
                        requester: ActorRef[DeviceManager.RespondAllTemperatures],
                        timeout: FiniteDuration,
                        context: ActorContext[DeviceGroupQuery.Command],
                        timers: TimerScheduler[DeviceGroupQuery.Command])
  extends AbstractBehavior[DeviceGroupQuery.Command](context) {

  import DeviceGroupQuery._
  import DeviceManager.DeviceNotAvailable
  import DeviceManager.DeviceTimedOut
  import DeviceManager.RespondAllTemperatures
  import DeviceManager.Temperature
  import DeviceManager.TemperatureNotAvailable
  import DeviceManager.TemperatureReading

  /**
   * 启动actor时，设置actor的超时时间
   */
  timers.startSingleTimer(CollectionTimeout, CollectionTimeout, timeout)
  /**
   * 协议转换方法，在调用接收时转换，因为device的command和deviceGroupQuery的command不共享
   */
  private val respondTemperatureAdapter = context.messageAdapter(WrappedRespondTemperature.apply)

  /**
   * actor创建时，给所有设备发送温度查询请求，并监控设备是否已经死亡
   */
  deviceIdToActor.foreach {
    case (deviceId, device) =>
      //监控每个device是否已经死亡
      context.watchWith(device, DeviceTerminated(deviceId))
      device ! Device.ReadTemperature(0, respondTemperatureAdapter)
  }
  //迄今为止已经收到的温度消息
  private var repliesSoFar = Map.empty[String, TemperatureReading]
  //仍旧在等待的设备
  private var stillWaiting = deviceIdToActor.keySet

  override def onMessage(msg: Command): Behavior[Command] =
    msg match {
        //接收发送给device已返回的消息，并处理消息
      case WrappedRespondTemperature(response) => onRespondTemperature(response)
        //接收已经死亡的设备消息
      case DeviceTerminated(deviceId)          => onDeviceTerminated(deviceId)
        //接收当前actor已经超时的消息
      case CollectionTimeout                   => onCollectionTimout()
    }

  /**
   * 处理已经接收到的消息
   * @param response
   * @return
   */
  private def onRespondTemperature(response: Device.RespondTemperature): Behavior[Command] = {
    //获取接收到的读数，如果有就返回数据对象，没有就返回未活跃状态，？设备未活跃消息是哪里发送的？
    val reading = response.value match {
      case Some(value) => Temperature(value)
      case None        => TemperatureNotAvailable
    }

    val deviceId = response.deviceId
    //将已经收到的数据存储到已收消息容器中
    repliesSoFar += (deviceId -> reading)
    //从未受消息中去掉该设备号
    stillWaiting -= deviceId
    //接收消息后，触发消息接收完毕检测
    respondWhenAllCollected()
  }

  /**
   * 接收设备死亡消息
   * @param deviceId
   * @return
   */
  private def onDeviceTerminated(deviceId: String): Behavior[Command] = {
    //如果还有在等待的设备，就存入设备未活跃消息，并且从等待设备中去除该设备
    if (stillWaiting(deviceId)) {
      repliesSoFar += (deviceId -> DeviceNotAvailable)
      stillWaiting -= deviceId
    }
    respondWhenAllCollected()
  }

  /**
   * 等待超时消息
   * @return
   */
  private def onCollectionTimout(): Behavior[Command] = {
    //直接将超时未返回的设备设置未超时消息，并清空超时设备
    repliesSoFar ++= stillWaiting.map(deviceId => deviceId -> DeviceTimedOut)
    stillWaiting = Set.empty
    respondWhenAllCollected()
  }

  /**
   * 消息完全返回检查
   * @return
   */
  private def respondWhenAllCollected(): Behavior[Command] = {
    //如果仍旧等待集合未空，就返回所有消息，并且关闭该actor
    if (stillWaiting.isEmpty) {
      requester ! RespondAllTemperatures(requestId, repliesSoFar)
      Behaviors.stopped
    } else {
      this
    }
  }
}