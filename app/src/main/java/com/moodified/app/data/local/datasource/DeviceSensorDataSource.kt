package com.moodified.app.data.local.datasource

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceSensorDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // --- Sleep Tracking Sensors ---

    fun hasSignificantMotionSensor(): Boolean {
        return sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION) != null
    }

    fun queryLatestArStillConfidence(): Int {
        // Reserved for future Activity Recognition integration in Sleep Inference
        return 0
    }

    // --- Activity Tracking Sensors ---

    fun getStepCounterSensor(): Sensor? {
        return sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    }

    fun registerStepListener(listener: SensorEventListener, sensor: Sensor, delay: Int, maxLatency: Int) {
        sensorManager.registerListener(listener, sensor, delay, maxLatency)
    }

    fun unregisterStepListener(listener: SensorEventListener) {
        sensorManager.unregisterListener(listener)
    }

    fun getActivityRecognitionClient(): ActivityRecognitionClient {
        return ActivityRecognition.getClient(context)
    }
}