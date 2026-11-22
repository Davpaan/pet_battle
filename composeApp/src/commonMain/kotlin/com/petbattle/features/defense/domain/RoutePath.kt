package com.petbattle.features.defense.domain

import com.petbattle.features.defense.domain.entities.MapPoint
import com.petbattle.features.defense.domain.entities.MapRoute

/** A [MapRoute] medida para recorrerla a velocidad constante: distancia -> posición. */
class RoutePath(route: MapRoute) {
    val points: List<MapPoint> = route.points
    private val cumulative: DoubleArray

    init {
        require(points.size >= 2) { "A route needs at least two points" }
        cumulative = DoubleArray(points.size)
        for (i in 1 until points.size) {
            cumulative[i] = cumulative[i - 1] + points[i - 1].distanceTo(points[i])
        }
    }

    val length: Double get() = cumulative.last()

    /** Posición tras recorrer [distance] desde el inicio (limitada a los extremos). */
    fun positionAt(distance: Double): MapPoint {
        if (distance <= 0) return points.first()
        if (distance >= length) return points.last()
        var i = 1
        while (cumulative[i] < distance) i++
        val a = points[i - 1]
        val b = points[i]
        val seg = cumulative[i] - cumulative[i - 1]
        val t = if (seg == 0.0) 0.0 else (distance - cumulative[i - 1]) / seg
        return MapPoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
    }
}
