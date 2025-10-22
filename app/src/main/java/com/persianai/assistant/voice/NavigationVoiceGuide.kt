package com.persianai.assistant.voice

import android.content.Context
import android.location.Location
import android.util.Log

class NavigationVoiceGuide(context: Context) {
    
    private val voiceAlerts = PersianVoiceAlerts(context)
    private var lastWarningDistance = -1
    private var hasWarnedCamera = false
    
    // تشخیص نزدیکی به پیچ
    fun checkTurnWarning(currentLoc: Location, turnLoc: Location, turnType: TurnType) {
        val distance = currentLoc.distanceTo(turnLoc).toInt()
        
        when {
            distance <= 50 && lastWarningDistance != 50 -> {
                lastWarningDistance = 50
                when(turnType) {
                    TurnType.RIGHT -> voiceAlerts.turnRight(50)
                    TurnType.LEFT -> voiceAlerts.turnLeft(50)
                    TurnType.STRAIGHT -> voiceAlerts.goStraight(50)
                }
            }
            distance <= 200 && lastWarningDistance != 200 -> {
                lastWarningDistance = 200
                when(turnType) {
                    TurnType.RIGHT -> voiceAlerts.turnRight(200)
                    TurnType.LEFT -> voiceAlerts.turnLeft(200)
                    TurnType.STRAIGHT -> voiceAlerts.goStraight(200)
                }
            }
            distance <= 500 && lastWarningDistance != 500 -> {
                lastWarningDistance = 500
                when(turnType) {
                    TurnType.RIGHT -> voiceAlerts.turnRight(500)
                    TurnType.LEFT -> voiceAlerts.turnLeft(500)
                    TurnType.STRAIGHT -> voiceAlerts.goStraight(500)
                }
            }
        }
    }
    
    // بررسی سرعت
    fun checkSpeed(currentSpeed: Float, speedLimit: Int) {
        if (currentSpeed > speedLimit + 10) {
            voiceAlerts.speedWarning(currentSpeed.toInt(), speedLimit)
        }
    }
    
    // هشدار دوربین
    fun checkCamera(currentLoc: Location, cameraLoc: Location) {
        val distance = currentLoc.distanceTo(cameraLoc).toInt()
        
        if (distance <= 500 && !hasWarnedCamera) {
            voiceAlerts.cameraAhead(distance)
            hasWarnedCamera = true
        } else if (distance > 500) {
            hasWarnedCamera = false
        }
    }
    
    fun arrived() {
        voiceAlerts.arrived()
    }
    
    fun shutdown() {
        voiceAlerts.shutdown()
    }
    
    enum class TurnType {
        RIGHT, LEFT, STRAIGHT
    }
}
