package com.example.iot

import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext}

/**
 * 设备管理中心
 */
object DeviceManager {

  sealed trait Command

  /**
   * 请求返回注册的该设备
   * @param groupId
   * @param deviceId
   * @param replyTo
   */
  final case class RequestTrackDevice(groupId: String, deviceId: String, replyTo: ActorRef[DeviceRegistered])
    extends DeviceManager.Command
      with DeviceGroup.Command

  /**
   * 返回的已注册设备
   * @param device
   */
  final case class DeviceRegistered(device: ActorRef[Device.Command])

  /**
   * 请求返回列表
   * @param requestId
   * @param groupId
   * @param replyTo
   */
  final case class RequestDeviceList(requestId: Long, groupId: String, replyTo: ActorRef[ReplyDeviceList])
    extends DeviceManager.Command
      with DeviceGroup.Command

  /**
   * 返回设备列表
   * @param requestId
   * @param ids
   */
  final case class ReplyDeviceList(requestId: Long, ids: Set[String])

  /**
   * 设备组关闭请求
   * @param groupId
   */
  private final case class DeviceGroupTerminated(groupId: String) extends DeviceManager.Command

  /**
   * 请求返回所有设备的温度快照
   * @param requestId
   * @param groupId
   * @param replyTo
   */
  final case class RequestAllTemperatures(requestId: Long, groupId: String, replyTo: ActorRef[RespondAllTemperatures])
    extends DeviceGroupQuery.Command
      with DeviceGroup.Command
      with DeviceManager.Command

  /**
   * 返回设备的温度快照
   * @param requestId
   * @param temperatures
   */
  final case class RespondAllTemperatures(requestId: Long, temperatures: Map[String, TemperatureReading])

  /**
   * 请求返回温度
   */
  sealed trait TemperatureReading
  final case class Temperature(value: Double) extends TemperatureReading
  case object TemperatureNotAvailable extends TemperatureReading
  case object DeviceNotAvailable extends TemperatureReading
  case object DeviceTimedOut extends TemperatureReading
}
class DeviceManager(context: ActorContext[DeviceManager.Command])
  extends AbstractBehavior[DeviceManager.Command](context) {
  import DeviceManager._

  var groupIdToActor = Map.empty[String, ActorRef[DeviceGroup.Command]]

  context.log.info("DeviceManager started")

  override def onMessage(msg: Command): Behavior[Command] =
    msg match {
        //请求返回已注册的设备，如果有group就将消息转发到group中，如果没有就创建group，然后在转发
      case trackMsg @ RequestTrackDevice(groupId, _, replyTo) =>
        groupIdToActor.get(groupId) match {
          case Some(ref) =>
            ref ! trackMsg
          case None =>
            context.log.info("Creating device group actor for {}", groupId)
            val groupActor = context.spawn(DeviceGroup(groupId), "group-" + groupId)
            context.watchWith(groupActor, DeviceGroupTerminated(groupId))
            groupActor ! trackMsg
            groupIdToActor += groupId -> groupActor
        }
        this

      case req @ RequestDeviceList(requestId, groupId, replyTo) =>
        groupIdToActor.get(groupId) match {
          case Some(ref) =>
            ref ! req
          case None =>
            replyTo ! ReplyDeviceList(requestId, Set.empty)
        }
        this

      case DeviceGroupTerminated(groupId) =>
        context.log.info("Device group actor for {} has been terminated", groupId)
        groupIdToActor -= groupId
        this
    }

  override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
    case PostStop =>
      context.log.info("DeviceManager stopped")
      this
  }

}