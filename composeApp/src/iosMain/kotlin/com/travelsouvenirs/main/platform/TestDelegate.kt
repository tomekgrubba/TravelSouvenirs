package com.travelsouvenirs.main.platform
import platform.MapKit.*
import platform.darwin.NSObject
class TestDelegate : NSObject(), MKMapViewDelegateProtocol {
    override fun mapView(mapView: MKMapView, didSelectAnnotationView: MKAnnotationView) {}
}
