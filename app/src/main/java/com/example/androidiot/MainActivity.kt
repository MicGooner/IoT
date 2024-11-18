import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import info.mqtt.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.json.JSONObject

class MainActivity : AppCompatActivity(), SensorEventListener {
    private companion object {
        const val BROKER = "tcp://broker.hivemq.com:1883"
        const val TOPIC = "android/sensors"
        const val CLIENT_ID = "AndroidIoTDevice-${System.currentTimeMillis()}"
    }

    private val sensorManager by lazy {
        getSystemService(SENSOR_SERVICE) as SensorManager
    }

    private val accelerometer by lazy {
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    private lateinit var sensorDataText: TextView
    private lateinit var mqttClient: MqttAndroidClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sensorDataText = findViewById(R.id.sensorData)

        setupMQTT()
    }

    private fun setupMQTT() {
        mqttClient = MqttAndroidClient(applicationContext, BROKER, CLIENT_ID)

        try {
            mqttClient.connect(null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    println("Conexión MQTT exitosa")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    println("Error en la conexión MQTT: ${exception?.message}")
                }
            })
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val sensorData = JSONObject().apply {
                put("x", event.values[0])
                put("y", event.values[1])
                put("z", event.values[2])
                put("timestamp", System.currentTimeMillis())
            }

            val displayText = """
                X: %.2f
                Y: %.2f
                Z: %.2f
            """.trimIndent().format(
                event.values[0],
                event.values[1],
                event.values[2]
            )

            sensorDataText.text = displayText

            lifecycleScope.launch(Dispatchers.IO) {
                publishSensorData(sensorData.toString())
            }
        }
    }

    private fun publishSensorData(data: String) {
        try {
            if (mqttClient.isConnected) {
                val message = MqttMessage(data.toByteArray())
                mqttClient.publish(TOPIC, message)
            }
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No se requiere implementación
    }

    override fun onDestroy() {
        try {
            mqttClient.disconnect()
        } catch (e: MqttException) {
            e.printStackTrace()
        }
        super.onDestroy()
    }
}