package com.example.organizadoremocional.core.utils

import android.util.Log
import com.example.organizadoremocional.model.Periodo
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 *
 */
object DateUtils {
    private const val TAG = "DateUtils"

    /**
     * Calcula el intervalo de fechas para un periodo dado.
     */
    fun getIntervalo(periodo: Periodo): Pair<Date, Date> {
        val cal = Calendar.getInstance()

        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        val fin = cal.time

        when (periodo) {
            Periodo.SEMANA -> {
                val diaSemana = cal.get(Calendar.DAY_OF_WEEK)
                val diasARestar = when(diaSemana) {
                    Calendar.SUNDAY -> 6
                    else -> diaSemana - Calendar.MONDAY
                }

                cal.add(Calendar.DAY_OF_MONTH, -diasARestar)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
            }
            Periodo.MES -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
            }
            Periodo.ANIO -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
            }

        }
        val inicio = cal.time

        return Pair(inicio, fin)
    }
}