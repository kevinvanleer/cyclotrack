import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.kvl.cyclotrack.ExternalSensor
import com.kvl.cyclotrack.R

class SensorGroup : ConstraintLayout {
    var groupIcon: ImageView
    var groupName: TextView
    var sensorList: LinearLayout

    init {
        View.inflate(context, R.layout.view_sensor_group, this)
        groupIcon = findViewById(R.id.sensorGroup_imageView_groupTypeIcon)
        groupName = findViewById(R.id.sensorGroup_textView_name)
        sensorList = findViewById(R.id.sensorGroup_linearLayout_sensors)
    }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    )

    fun initialize(name: String, type: Long?, sensors: Array<ExternalSensor>) {
        groupName.text = name
        when (type) {
            null -> R.drawable.ic_baseline_accessibility_new_24
            else -> R.drawable.ic_baseline_directions_bike_24
        }.let { groupIcon.setImageResource(it) }
        if (sensors.isNotEmpty()) populate(sensors) else sensorList.addView(
            inflate(
                context,
                R.layout.view_empty_sensor_group_placeholder,
                null
            )
        )
    }

    fun populate(sensors: Array<ExternalSensor>) {
        sensorList.removeAllViews()
        sensors.forEach { sensor ->
            SensorInfo(context).apply {
                populate(sensor)
                sensorList.addView(this)
            }
            Space(context).apply {
                minimumHeight = 8
                sensorList.addView(this)
            }
        }
    }
}