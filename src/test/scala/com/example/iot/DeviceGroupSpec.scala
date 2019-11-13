package com.example.iot

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.example.iot.Device.{RecordTemperature, TemperatureRecorded}
import com.example.iot.DeviceManager.{DeviceRegistered, RequestTrackDevice}
import org.scalatest.WordSpecLike

class DeviceGroupSpec extends ScalaTestWithActorTestKit with WordSpecLike {
  "be able to register a device actor" in {
    val probe = createTestProbe[DeviceRegistered]()
    val groupActor = spawn(DeviceGroup("group"))

    groupActor ! RequestTrackDevice("group", "device1", probe.ref)
    val registered1 = probe.receiveMessage()
    val deviceActor1 = registered1.device

    // another deviceId
    groupActor ! RequestTrackDevice("group", "device2", probe.ref)
    val registered2 = probe.receiveMessage()
    val deviceActor2 = registered2.device
    deviceActor1 should !==(deviceActor2)

    // Check that the device actors are working
    val recordProbe = createTestProbe[TemperatureRecorded]()
    deviceActor1 ! RecordTemperature(requestId = 0, 1.0, recordProbe.ref)
    recordProbe.expectMessage(TemperatureRecorded(requestId = 0))
    deviceActor2 ! Device.RecordTemperature(requestId = 1, 2.0, recordProbe.ref)
    recordProbe.expectMessage(Device.TemperatureRecorded(requestId = 1))
  }

  "ignore requests for wrong groupId" in {
    val probe = createTestProbe[DeviceRegistered]()
    val groupActor = spawn(DeviceGroup("group"))

    groupActor ! RequestTrackDevice("wrongGroup", "device1", probe.ref)
    probe.expectNoMessage(500.milliseconds)
  }
}
