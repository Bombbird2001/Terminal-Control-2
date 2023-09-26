package com.bombbird.terminalcontrol2.systems

import com.bombbird.terminalcontrol2.networking.dataclasses.IndividualSectorData
import com.bombbird.terminalcontrol2.screens.RadarScreen
import com.bombbird.terminalcontrol2.ui.panes.StatusPane

fun loadAllFamilies() {
    AISystem.initialise()
    ControlStateSystem.initialise()
    ControlStateSystemInterval.initialise()
    ControlStateSystemIntervalClient.initialise()
    DataSystem.initialise()
    DataSystemClient.initialise()
    DataSystemIntervalClient.initialise()
    PhysicsSystem.initialise()
    PhysicsSystemClient.initialise()
    PhysicsSystemInterval.initialise()
    PhysicsSystemIntervalClient.initialise()
    RenderingSystemClient.initialise()
    TrafficSystemInterval.initialise()
    TrafficSystemIntervalClient.initialise()

    RadarScreen.initialise()
    StatusPane.initialise()
    IndividualSectorData.initialise()
}