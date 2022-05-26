import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.kvl.cyclotrack.ExternalSensor
import com.kvl.cyclotrack.ExternalSensorFeatures
import com.kvl.cyclotrack.R

class SensorInfo : ConstraintLayout {
    val logTag = "SensorInfo"
    var sensorTypeIcon: ImageView
    var sensorName: TextView
    var macAddress: TextView
    var batteryLevel: TextView
    var batteryLevelIcon: ImageView

    init {
        View.inflate(context, R.layout.view_sensor_info, this)
        sensorTypeIcon = findViewById(R.id.sensorInfo_imageView_sensorTypeIcon)
        sensorName = findViewById(R.id.sensorInfo_textView_name)
        macAddress = findViewById(R.id.sensorInfo_textView_macAddress)
        batteryLevel = findViewById(R.id.sensorInfo_textView_batteryLevel)
        batteryLevelIcon = findViewById(R.id.sensorInfo_imageView_batteryLevelIcon)
    }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    )

    fun populate(sensor: ExternalSensor) {
        sensorName.text = sensor.name
        macAddress.text = sensor.address
        sensor.batteryLevel?.let { batteryLevel.text = "${it}%" }
        Log.d(logTag, "$sensor")
        sensor.features.let { ft ->
            when {
                ((ft?.and(ExternalSensorFeatures().HRM)) == ExternalSensorFeatures().HRM)
                -> R.drawable.ic_baseline_monitor_heart_18
                ((ft?.and(ExternalSensorFeatures().SPEED)) == ExternalSensorFeatures().SPEED)
                -> R.drawable.ic_baseline_speed_18
                ((ft?.and(ExternalSensorFeatures().CADENCE)) == ExternalSensorFeatures().CADENCE)
                -> R.drawable.ic_baseline_360_24
                ((ft?.and(ExternalSensorFeatures().POWER)) == ExternalSensorFeatures().POWER)
                -> R.drawable.ic_baseline_power_18
                else -> R.drawable.ic_baseline_question_mark_24
            }.let {
                sensorTypeIcon.setImageResource(it)
            }
        }
        sensor.batteryLevel?.let { level ->
            when {
                (level > 99) -> R.drawable.ic_baseline_battery_full_24
                (level >= 90) -> R.drawable.ic_baseline_battery_6_bar_24
                (level >= 75) -> R.drawable.ic_baseline_battery_5_bar_24
                (level >= 50) -> R.drawable.ic_baseline_battery_4_bar_24
                (level >= 25) -> R.drawable.ic_baseline_battery_3_bar_24
                (level >= 15) -> R.drawable.ic_baseline_battery_2_bar_24
                (level >= 0) -> R.drawable.ic_battery_alert_18dp
                else -> R.drawable.ic_baseline_battery_unknown_18
            }.let {
                batteryLevelIcon.setImageResource(it)
            }
        }
    }
}