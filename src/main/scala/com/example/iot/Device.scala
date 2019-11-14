package com.example.iot
import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import com.example.iot.Device.{Command, Passivate, ReadTemperature, RecordTemperature, RespondTemperature, TemperatureRecorded}

/**
 * 设备对象
 */
object Device {
  sealed trait Command
  //请求阅读温度，返回温度数据
  final case class ReadTemperature(requestId: Long,replyTo: ActorRef[RespondTemperature]) extends Command
  //温度返回对象
  final case class RespondTemperature(requestId: Long, deviceId: String, value: Option[Double])

  final case class RecordTemperature(requestId: Long, value: Double, replyTo: ActorRef[TemperatureRecorded])
    extends Command
  final case class TemperatureRecorded(requestId: Long)

  case object Passivate extends Command



  def apply(groupId: String, deviceId: String): Behavior[Command] =
    Behaviors.setup(context => new Device(context, groupId, deviceId))
}
class Device(context: ActorContext[Device.Command], groupId: String, deviceId: String)
  extends AbstractBehavior[Device.Command](context) {

  var lastTemperatureReading: Option[Double] = None

  context.log.info("Device actor {}-{} started", groupId, deviceId)

  override def onMessage(msg: Command): Behavior[Command] = {
    msg match {
        //纪录温度
      case RecordTemperature(id, value, replyTo) =>
        context.log.info("Recorded temperature reading {} with {}", value, id)
        lastTemperatureReading = Some(value)
        replyTo ! TemperatureRecorded(id)
        this
        //读取温度
      case ReadTemperature(id, replyTo) =>
        replyTo ! RespondTemperature(id,deviceId, lastTemperatureReading)
        this
      case Passivate =>
        Behaviors.stopped
    }
  }

  override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
    case PostStop =>
      context.log.info("Device actor {}-{} stopped", groupId, deviceId)
      this
  }

}