package com.cycling.workitout.workout

import kotlin.math.cbrt
import kotlin.math.max

// Converts power to virtual road speed using standard cycling physics (aero + rolling + drivetrain).
class VirtualSpeedEstimator(
    riderWeightKg: Double,
    private val bikeWeightKg: Double = 8.0,
    private val cdA: Double = 0.324,
    private val crr: Double = 0.005,
    private val drivetrainEfficiency: Double = 0.977,
    private val airDensityKgPerM3: Double = 1.225
) {
    private val totalMassKg = riderWeightKg + bikeWeightKg

    fun speedMpsFor(powerWatts: Int): Float {
        if (powerWatts <= 0) return 0f
        val pEffective = powerWatts * drivetrainEfficiency
        val dragCoef = 0.5 * airDensityKgPerM3 * cdA         // multiplies v³
        val rollCoef = crr * totalMassKg * GRAVITY_MPS2      // multiplies v

        // Newton-Raphson on f(v) = dragCoef·v³ + rollCoef·v − pEffective
        var v = max(cbrt(pEffective / dragCoef), 1.0)        // drag-dominant seed
        repeat(6) {
            val fv = dragCoef * v * v * v + rollCoef * v - pEffective
            val fPrime = 3 * dragCoef * v * v + rollCoef
            v -= fv / fPrime
            if (v < 0.1) v = 0.1
        }
        return v.toFloat()
    }

    companion object {
        const val GRAVITY_MPS2 = 9.8067
    }
}
