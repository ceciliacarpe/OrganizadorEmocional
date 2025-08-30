package com.example.organizadoremocional.ui.estadisticas

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.organizadoremocional.R
import com.example.organizadoremocional.core.utils.DateUtils
import com.example.organizadoremocional.model.EstadoDeAnimoTipo
import com.example.organizadoremocional.model.Periodo
import com.example.organizadoremocional.model.Tarea
import com.example.organizadoremocional.repository.EstadoDeAnimoRepository
import com.example.organizadoremocional.repository.TareaRepository
import com.example.organizadoremocional.ui.home.BaseActivity
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Muestra las estadísticas de tareas pendientes y completadas y el tiempo pasado en cada estado de
 * ánimo.
 */
class EstadisticasActivity : BaseActivity() {
    private lateinit var spinnerPeriodo: Spinner
    private lateinit var barChart: BarChart
    private lateinit var pieChart: PieChart
    private val tareaRepo = TareaRepository()
    private val animoRepo = EstadoDeAnimoRepository()
    private val sdfEE = SimpleDateFormat("EEEE", Locale("es"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_estadisticas)
        spinnerPeriodo = findViewById(R.id.spinnerPeriodo)
        barChart = findViewById(R.id.barChartTareas)
        pieChart = findViewById(R.id.pieChartEstados)

        setupPieChart()

        //Selección de periodo
        spinnerPeriodo.adapter = ArrayAdapter.createFromResource(
            this, R.array.periodos, android.R.layout.simple_spinner_item
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        spinnerPeriodo.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(p: AdapterView<*>?) {}
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val per = p!!.getItemAtPosition(pos) as String
                renderPeriodo(per)
            }
        }

        renderPeriodo("Día")
    }

    private fun mapPeriodoStringToEnum(periodo: String): Periodo {
        return when (periodo.uppercase(Locale.getDefault())) {
            "SEMANA" -> Periodo.SEMANA
            "MES" -> Periodo.MES
            "AÑO" -> Periodo.ANIO
            else -> Periodo.SEMANA
        }
    }


    /**
     * Gráfico de estado de ánimo
     */
    private fun setupPieChart() {
        pieChart.apply {
            description.isEnabled = false
            setUsePercentValues(true)
            setExtraOffsets(5f, 10f, 5f, 5f)
            dragDecelerationFrictionCoef = 0.95f

            // Configuración del centro
            isDrawHoleEnabled = true
            setHoleColor(Color.TRANSPARENT)
            setTransparentCircleColor(Color.WHITE)
            setTransparentCircleAlpha(20)
            holeRadius = 35f
            transparentCircleRadius = 61f

            setDrawCenterText(true)
            centerText = "Estados emocionales"

            rotationAngle = 0f
            isRotationEnabled = true
            isHighlightPerTapEnabled = true

        }
    }

    private fun renderPeriodo(periodo: String) {
        barChart.clear()
        barChart.fitScreen()
        pieChart.clear()

        lifecycleScope.launch {
            // Gráfico de barras para tareas
            val (labels, pendientes, completadas) = when(periodo) {
                "Día" -> {
                    val asignadas = tareaRepo.getTareasAsignadasHoyConAplazables()
                    val completadas = tareaRepo.getTareasCompletadasHoy()

                    Triple(listOf("Hoy"), listOf(asignadas.size.toFloat()), listOf(completadas.size.toFloat()))
                }

                "Semana" -> {
                    getForSemana(
                        pendFn = tareaRepo::getTareasAsignadasSemana,
                        compFn = tareaRepo::getTareasCompletadasSemana
                    )
                }
                "Mes" -> {
                    getForMes(
                        pendFn = tareaRepo::getTareasAsignadasMes,
                        compFn = tareaRepo::getTareasCompletadasMes
                    )
                }
                "Año" -> {
                    getForAnio(
                        pendFn = tareaRepo::getTareasAsignadasAnio,
                        compFn = tareaRepo::getTareasCompletadasAnio
                    )
                }
                else -> Triple(emptyList(), emptyList(), emptyList())
            }
            drawChart(labels, pendientes, completadas, periodo)

            // Gráfico circular para estados de ánimo
            val tiempos: Map<EstadoDeAnimoTipo, Long> = when (periodo) {
                "Día" -> animoRepo.getTiempoPorEstadoEnDia(Date())
                "Semana", "Mes", "Año" -> {
                    val (start, end) = DateUtils.getIntervalo(mapPeriodoStringToEnum(periodo))
                    animoRepo.getTiempoPorEstadoEnIntervalo(start, end)
                }
                else -> emptyMap()
            }

            drawPieChartDesdeTiempos(tiempos, periodo)
        }
    }


    private fun drawPieChartDesdeTiempos(
        tiempos: Map<EstadoDeAnimoTipo, Long>,
        periodo: String
    ) {
        if (tiempos.isEmpty()) {
            pieChart.setNoDataText("No hay datos para este periodo")
            pieChart.invalidate()
            return
        }

        val totalMinutos = tiempos.values.sum()
        if (totalMinutos == 0L) {
            pieChart.setNoDataText("Sin duración registrada")
            pieChart.invalidate()
            return
        }

        val entradas = tiempos.map { (tipo, minutos) ->
            PieEntry(minutos.toFloat(), getNombreEstadoEmocional(tipo.name))
        }

        val tipos = tiempos.keys.toList()
        val colores = getColoresEstadosAnimo(tipos)

        val dataSet = PieDataSet(entradas, "").apply {
            setColors(*colores)
            valueTextSize = 12f
            valueTextColor = Color.WHITE
            sliceSpace = 3f
            selectionShift = 5f
        }

        val data = PieData(dataSet).apply {
            setValueFormatter(PercentFormatter(pieChart))
            setValueTextSize(11f)
            setValueTextColor(Color.WHITE)
        }

        pieChart.data = data
        pieChart.centerText = "Tiempo por estado de ánimo"
        pieChart.highlightValues(null)
        pieChart.invalidate()
        pieChart.animateY(1400)
    }

    private fun getNombreEstadoEmocional(tipoAnimo: String?): String {
        return when(tipoAnimo) {
            "MOTIVADO" -> "Motivado"
            "ESTRESADO" -> "Estresado"
            "DESMOTIVADO" -> "Desmotivado"
            "RELAJADO" -> "Relajado"
            else -> tipoAnimo?.replaceFirstChar { it.uppercaseChar() } ?: "Desconocido"
        }
    }

    private fun getColoresEstadosAnimo(keys: List<EstadoDeAnimoTipo>): IntArray {
        val colorMap = mapOf(
            EstadoDeAnimoTipo.MOTIVADO  to R.color.colorPriorityMediumSelected,
            EstadoDeAnimoTipo.ESTRESADO to R.color.estresado_primary,
            EstadoDeAnimoTipo.DESMOTIVADO to R.color.motivado_primary,
            EstadoDeAnimoTipo.RELAJADO to R.color.colorPriorityLowSelected
        )

        return keys.map { tipo ->
            val resId = colorMap[tipo] ?: R.color.colorAccent
            ContextCompat.getColor(this, resId)
        }.toIntArray()
    }

    /**
     * Obtiene los datos para la vista de semana (Lunes hasta el día actual)
     */
    private suspend fun getForSemana(
        pendFn: suspend() -> List<Tarea>,
        compFn: suspend() -> List<Tarea>
    ): Triple<List<String>, List<Float>, List<Float>> {
        val pend = pendFn()
        val comp = compFn()

        val calendar = Calendar.getInstance().apply { zeroTime() }
        val today    = Calendar.getInstance().apply { zeroTime() }
        while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }

        val diff = ((today.timeInMillis - calendar.timeInMillis) / (24 * 60 * 60 * 1000)).toInt()
        val slots = List(diff + 1) { i ->
            Calendar.getInstance().apply {
                time = calendar.time
                add(Calendar.DAY_OF_YEAR, i)
            }
        }

        val labels = slots.map {
            sdfEE.format(it.time)
                .replaceFirstChar { c -> c.uppercaseChar() }
        }

        val pCounts = slots.map { slot ->
            val sDate = slot.time
            pend.count { t ->
                val fr = t.fechaRealizar!!
                !t.completada &&
                        ( sameSlot(fr, sDate, 7)
                                || (fr.before(sDate) && t.aplazar)
                                )
            }.toFloat()
        }

        val cCounts = slots.map { slot ->
            val sDate = slot.time
            comp.count { t ->
                val fc = t.fechaCompletada!!
                sameSlot(fc, sDate, 7)
            }.toFloat()
        }

        return Triple(labels, pCounts, cCounts)
    }


    /**
     * Obtiene los datos para la vista del mes
     */
    private suspend fun getForMes(
        pendFn: suspend() -> List<Tarea>,
        compFn: suspend() -> List<Tarea>
    ): Triple<List<String>, List<Float>, List<Float>> {
        val pend = pendFn()
        val comp = compFn()

        val calendar = Calendar.getInstance().apply { zeroTime(); set(Calendar.DAY_OF_MONTH, 1) }
        val today    = Calendar.getInstance().apply { zeroTime() }
        val dayCount = today.get(Calendar.DAY_OF_MONTH)
        val slots = List(dayCount) { i ->
            Calendar.getInstance().apply {
                time = calendar.time
                add(Calendar.DAY_OF_MONTH, i)
            }
        }

        val etiquetas = slots.map { SimpleDateFormat("d", Locale.getDefault()).format(it.time) }

        val pCounts = slots.map { slot ->
            val sDate = slot.time
            pend.count { t ->
                val fr = t.fechaRealizar!!
                !t.completada && (isSameDay(fr,sDate) || (fr.before(sDate) && t.aplazar))
            }.toFloat()
        }

        val cCounts = slots.map { slot ->
            val sDate = slot.time
            comp.count { t ->
                val fc = t.fechaCompletada!!
                isSameDay(fc, sDate)
            }.toFloat()
        }

        return Triple(etiquetas, pCounts, cCounts)
    }

    /**
     * Obtiene los datos para la vista de año
     */
    private suspend fun getForAnio(
        pendFn: suspend() -> List<Tarea>,
        compFn: suspend() -> List<Tarea>
    ): Triple<List<String>, List<Float>, List<Float>> {
        val pend = pendFn()
        val comp = compFn()

        val calendar = Calendar.getInstance().apply { zeroTime(); set(Calendar.DAY_OF_YEAR, 1) }
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
        val slots = List(currentMonth + 1) { i ->
            Calendar.getInstance().apply {
                time = calendar.time
                set(Calendar.MONTH, i)
            }
        }

        val etiquetas = slots.map {
            it.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale("es"))
                ?.replaceFirstChar { c -> c.uppercaseChar() } ?: ""
        }

        val pCounts = slots.map { slot ->
            val sDate = slot.time
            pend.count { t ->
                val fr = t.fechaRealizar!!
                !t.completada &&
                        (sameSlot(fr, sDate, 12) || (fr.before(sDate) && t.aplazar))
            }.toFloat()
        }

        val cCounts = slots.map { slot ->
            val sDate = slot.time
            comp.count { t ->
                val fc = t.fechaCompletada!!
                sameSlot(fc, sDate, 12)
            }.toFloat()
        }

        return Triple(etiquetas, pCounts, cCounts)
    }



    /** compara por día (count≠12) o por mes (count==12) */
    private fun sameSlot(d1: Date, d2: Date, count: Int): Boolean {
        val c1 = Calendar.getInstance().apply { time=d1; zeroTime() }
        val c2 = Calendar.getInstance().apply { time=d2; zeroTime() }
        return if(count==12) {
            c1.get(Calendar.YEAR)==c2.get(Calendar.YEAR) &&
                    c1.get(Calendar.MONTH)==c2.get(Calendar.MONTH)
        } else {
            c1.time==c2.time
        }
    }



    private fun drawChart(labels: List<String>, y1: List<Float>, y2: List<Float>, periodo: String) {
        barChart.apply {
            description.isEnabled = false
            axisRight.isEnabled   = false
            setDrawValueAboveBar(false)

            axisLeft.apply {
                axisMinimum = 0f
                granularity = 1f
                isGranularityEnabled = true
                valueFormatter = object: ValueFormatter() {
                    override fun getFormattedValue(value: Float) = value.toInt().toString()
                }
            }

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)

                granularity = 1f
                isGranularityEnabled = true

                when(periodo) {
                    "Mes" -> {
                        labelRotationAngle = 0f
                        textSize = 9f
                        setLabelCount(labels.size, false)
                    }
                    "Año" -> {
                        labelRotationAngle = 45f
                        textSize = 10f
                        setLabelCount(labels.size, false)
                    }
                    "Semana" -> {
                        labelRotationAngle = 45f
                        textSize = 10f
                        setLabelCount(labels.size, false)
                    }
                    else -> {
                        labelRotationAngle = 0f
                        textSize = 12f
                        setLabelCount(labels.size, false)
                    }
                }

                valueFormatter = IndexAxisValueFormatter(labels)
                spaceMin = 0.5f
                spaceMax = 0.5f

                setCenterAxisLabels(periodo != "Mes")

                setAvoidFirstLastClipping(true)
            }

            val setP = BarDataSet(
                y1.mapIndexed{ i,v-> BarEntry(i.toFloat(), v) },
                "Asigandas"
            ).apply {
                setDrawValues(true)
                valueTextSize = 10f
                setColors(intArrayOf(R.color.purple_500), context)
            }
            val setC = BarDataSet(
                y2.mapIndexed{ i,v-> BarEntry(i.toFloat(), v) },
                "Completadas"
            ).apply {
                setDrawValues(true)
                valueTextSize = 10f
                setColors(intArrayOf(R.color.teal_200), context)
            }

            val barWidth = when(periodo) {
                "Día" -> 0.4f
                "Semana" -> 0.35f
                "Mes" -> 0.25f
                "Año" -> 0.35f
                else -> 0.4f
            }
            val barSpace = when(periodo) {
                "Mes" -> 0.03f
                else -> 0.05f
            }
            val grpSpace = 1f - 2*barWidth - barSpace

            // Configurar datos
            data = BarData(setP, setC).apply {
                this.barWidth = barWidth
                val showValues = when(periodo) {
                    "Día" -> false
                    "Semana" -> false
                    "Mes" -> false
                    "Año" -> false
                    else -> labels.size <= 14
                }
                setDrawValues(showValues)
            }

            if (periodo == "Mes") {
                setFitBars(true)
                val pendientesDataSet = BarDataSet(
                    y1.mapIndexed { i, v -> BarEntry(i.toFloat(), v) },
                    "Asignadas"
                ).apply {
                    setDrawValues(false)
                    setColors(intArrayOf(R.color.purple_500), context)
                }

                val completadasDataSet = BarDataSet(
                    y2.mapIndexed { i, v -> BarEntry(i.toFloat() + barWidth, v) },
                    "Completadas"
                ).apply {
                    setDrawValues(false)
                    setColors(intArrayOf(R.color.teal_200), context)
                }

                // Configurar los datos sin agrupar
                data = BarData(pendientesDataSet, completadasDataSet).apply {
                    this.barWidth = barWidth
                }

                // Ajustar el rango para que las barras queden dentro de las etiquetas
                xAxis.axisMinimum = -0.5f
                xAxis.axisMaximum = labels.size - 0.5f
            } else {
                xAxis.axisMinimum = 0f
                xAxis.axisMaximum = data.getGroupWidth(grpSpace, barSpace) * labels.size
                groupBars(0f, grpSpace, barSpace)
                setFitBars(true)
            }

            isDragEnabled = true
            isScaleXEnabled = true
            isScaleYEnabled = false
            setPinchZoom(false)

            val visibleBars = when(periodo) {
                "Día" -> 1f
                "Semana" -> 7f
                "Mes" -> 7f
                "Año" -> 6f
                else -> labels.size.toFloat().coerceAtMost(7f)
            }

            setVisibleXRangeMinimum(1f)
            setVisibleXRangeMaximum(visibleBars)
            legend.isEnabled = true
            extraBottomOffset = if (periodo == "Mes") 15f else 10f

            animateY(500)
            invalidate()
        }
    }

    private fun Calendar.zeroTime() {
        set(Calendar.HOUR_OF_DAY,0)
        set(Calendar.MINUTE,0)
        set(Calendar.SECOND,0)
        set(Calendar.MILLISECOND,0)
    }

    private fun isSameDay(d1: Date, d2: Date): Boolean {
        val c1 = Calendar.getInstance().apply { time = d1; zeroTime() }
        val c2 = Calendar.getInstance().apply { time = d2; zeroTime() }
        return c1.time == c2.time
    }

}
