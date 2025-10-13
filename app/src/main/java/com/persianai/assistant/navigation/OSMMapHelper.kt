package com.persianai.assistant.navigation

import android.content.Context
import android.graphics.Color
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * کمک کننده برای OpenStreetMap
 */
object OSMMapHelper {
    
    fun initMap(context: Context, mapView: MapView) {
        Configuration.getInstance().load(
            context,
            context.getSharedPreferences("osm_prefs", Context.MODE_PRIVATE)
        )
        
        mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
        }
    }
    
    fun addMarker(mapView: MapView, lat: Double, lng: Double, title: String): Marker {
        val marker = Marker(mapView)
        marker.position = GeoPoint(lat, lng)
        marker.title = title
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        mapView.overlays.add(marker)
        return marker
    }
    
    fun drawRoute(mapView: MapView, points: List<Pair<Double, Double>>): Polyline {
        val line = Polyline()
        line.setPoints(points.map { GeoPoint(it.first, it.second) })
        line.outlinePaint.color = Color.BLUE
        line.outlinePaint.strokeWidth = 10f
        mapView.overlays.add(line)
        return line
    }
    
    fun moveCamera(mapView: MapView, lat: Double, lng: Double, zoom: Double = 15.0) {
        mapView.controller.setCenter(GeoPoint(lat, lng))
        mapView.controller.setZoom(zoom)
    }
}
