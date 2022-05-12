package com.a1tech.test;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import org.osmdroid.bonuspack.routing.OSRMRoadManager;
import org.osmdroid.bonuspack.routing.Road;
import org.osmdroid.bonuspack.routing.RoadManager;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;

import okhttp3.Route;

public class OsmdroidDemoMap extends FragmentActivity implements OnMapReadyCallback {
    private final int REQUEST_PERMISSIONS_REQUEST_CODE = 1;
    private final int ACCESS_LOCATION_REQUEST_CODE = 10001;
    private final String TAG = "OsmActivity";
    private MapView osMap = null;
    private GoogleMap gMap;
    private double mapZoomLevelCurrent = 18.0;
    private Location startLocation;
    private Location clientLocation;
    private Marker markerTaxi, markerClient;
    private Geocoder geocoder;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private com.google.android.gms.maps.model.Marker userLocationMarker;
    private List<com.google.android.gms.maps.model.Polyline> polylines = null;
    private Road road;
    private com.google.android.gms.maps.model.Polyline polyline;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        setContentView(R.layout.activity_main);

        getDataFromIntent();
        initOSMapView();
        initGMapFragment();
    }

    private void getDataFromIntent() {
        startLocation = new Location("");
        startLocation.setLatitude(41.534783);
        startLocation.setLongitude(60.601419);

        clientLocation = new Location("");
        clientLocation.setLatitude(41.563817);
        clientLocation.setLongitude(60.663904);
    }

    private void initOSMapView() {
        osMap = (MapView) findViewById(R.id.OSMap);
        osMap.setTileSource(TileSourceFactory.MAPNIK);

        requestPermissionsIfNecessary(new String[]{
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        });

        osMap.getController().setZoom(mapZoomLevelCurrent);
        osMap.addMapListener(new MapListener() {
            @Override
            public boolean onScroll(ScrollEvent event) {
                return false;
            }

            @Override
            public boolean onZoom(ZoomEvent event) {
                mapZoomLevelCurrent = event.getZoomLevel();
                return false;
            }
        });
//        41.552360, 60.627878
//        Location lastLocation = GPSTracker.getInstance().getLastLocation();
        osMap.getController().setCenter(new GeoPoint(startLocation));
        osMap.setMultiTouchControls(true);
        osMap.setBuiltInZoomControls(false);
        osMap.setMaxZoomLevel(20d);
        osMap.setMinZoomLevel(5d);

//        updateBtnNavigationVisibility();
        route();
        updateMarkerTaxi();
    }

    private void initGMapFragment() {
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.googleMap);
        mapFragment.getMapAsync(this);
        geocoder = new Geocoder(this);
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
    }

    private void route() {
        final GeoPoint startPoint = new GeoPoint(startLocation.getLatitude(), startLocation.getLongitude());
        final GeoPoint endPoint = new GeoPoint(clientLocation.getLatitude(), clientLocation.getLongitude());
        if (endPoint.getLatitude() == 0d || endPoint.getLongitude() == 0d) return;

        if (markerClient == null) {
            markerClient = createMarkerClient(endPoint);
            osMap.getOverlays().add(markerClient);
            osMap.invalidate();
        }

        if (startPoint.getLatitude() == 0d || startPoint.getLongitude() == 0d) return;

        AsyncTask.execute(() -> {
            RoadManager roadManager = new OSRMRoadManager(this);
            ArrayList<GeoPoint> wayPoints = new ArrayList<>();
            wayPoints.add(startPoint);
            wayPoints.add(endPoint);
            road = roadManager.getRoad(wayPoints);
            if (road.mStatus != Road.STATUS_OK) {
                runOnUiThread(this::showDialogRoutingTechnicalIssue);
                return;
            }
            Polyline roadOverlay = RoadManager.buildRoadOverlay(road);
            roadOverlay.setWidth(8);
            runOnUiThread(() -> {
                osMap.getOverlays().add(roadOverlay);
                addMarkerTaxi(new GeoPoint(startLocation.getLatitude(), startLocation.getLongitude()));
                osMap.invalidate();

//                for (int i = 0; i < road.mRouteHigh.size(); i++) {
//                    Log.e(TAG, "road " + road.mRouteHigh.get(i));
//                }
            });
        });
    }

    private void updateMarkerTaxi() {
        GeoPoint position = new GeoPoint(41.552360, 60.627878);
        if (markerTaxi == null) {
            addMarkerTaxi(position);
        } else {
            markerTaxi.setPosition(position);
            osMap.invalidate();
        }
    }

    private void addMarkerTaxi(GeoPoint position) {
        if (markerTaxi != null) {
            markerTaxi.remove(osMap);
        }
        markerTaxi = createMarkerTaxi(position);
        osMap.getOverlays().add(markerTaxi);
        osMap.invalidate();
    }

    private Marker createMarkerTaxi(GeoPoint position) {
        Marker marker = new Marker(osMap);
        marker.setPosition(position);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        marker.setIcon(ContextCompat.getDrawable(this, R.drawable.redcar));
        marker.setInfoWindow(null);
        return marker;
    }

    private void showDialogRoutingTechnicalIssue() {
//        new AlertDialog.Builder(this)
//                .setMessage(R.string.could_not_create_route)
//                .setPositiveButton(R.string.retry, (dialog, which) -> route())
//                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel())
//                .show();
    }

    private Marker createMarkerClient(GeoPoint position) {
        Marker marker = new Marker(osMap);
        marker.setPosition(position);
        marker.setAnchor(0.5f, 1.0f);
        marker.setIcon(ContextCompat.getDrawable(this, R.drawable.sos_marker));
        return marker;
    }

    @Override
    public void onResume() {
        super.onResume();
        osMap.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        osMap.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        ArrayList<String> permissionsToRequest = new ArrayList<>();
        for (int i = 0; i < grantResults.length; i++) {
            permissionsToRequest.add(permissions[i]);
        }
        if (permissionsToRequest.size() > 0) {
            ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest.toArray(new String[0]),
                    REQUEST_PERMISSIONS_REQUEST_CODE);
        }
    }

    private void requestPermissionsIfNecessary(String[] permissions) {
        ArrayList<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                // Permission is not granted
                permissionsToRequest.add(permission);
            }
        }
        if (permissionsToRequest.size() > 0) {
            ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest.toArray(new String[0]),
                    REQUEST_PERMISSIONS_REQUEST_CODE);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        gMap = googleMap;
        gMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
//            enableUserLocation();
//            zoomToUserLocation();
        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                //We can show user a dialog why this permission is necessary
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, ACCESS_LOCATION_REQUEST_CODE);
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, ACCESS_LOCATION_REQUEST_CODE);
            }
        }
        gMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
            @Override
            public void onMapLoaded() {

                LatLng latLng = new LatLng(startLocation.getLatitude(), startLocation.getLongitude());

                if (userLocationMarker == null) {
                    //Create a new marker
                    MarkerOptions markerOptions = new MarkerOptions();
                    markerOptions.position(latLng);
                    markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.redcar));
                    markerOptions.anchor((float) 0.5, (float) 0.5);
                    userLocationMarker = gMap.addMarker(markerOptions);
//            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17));
                }

                LatLng currentLatLng = new LatLng(startLocation.getLatitude(), startLocation.getLongitude());
                LatLng sosLatLng = new LatLng(clientLocation.getLatitude(), clientLocation.getLongitude());

//                Findroutes(currentLatLng, sosLatLng);

                //create marker options
                MarkerOptions sosMarker = new MarkerOptions().position(sosLatLng).title("Google Marker");

                //add marker on map
                gMap.addMarker(sosMarker);
                sosMarker.icon(BitmapDescriptorFactory.fromResource(R.drawable.custom_sos_marker));

                //test
                LatLngBounds.Builder builder = new LatLngBounds.Builder();

                //the include method will calculate the min and max bound.
                builder.include(sosMarker.getPosition());
                builder.include(currentLatLng);

                LatLngBounds bounds = builder.build();
                int width = getResources().getDisplayMetrics().widthPixels;
                int height = getResources().getDisplayMetrics().heightPixels;
                int padding = (int) (width * 0.20); // offset from edges of the map 10% of screen
                CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, width, height, padding);
                gMap.animateCamera(cu);
                drawRouteOnGMap();
            }
        });
    }

    private void drawRouteOnGMap() {
        ArrayList<LatLng> list = new ArrayList();

        Log.e(TAG, "listSize " + list.size());
        for (int i = 0; i < road.mRouteHigh.size(); i++) {
            list.add(new LatLng(road.mRouteHigh.get(i).getLatitude(), road.mRouteHigh.get(i).getLongitude()));
        }

        PolylineOptions options = new PolylineOptions().width(5).color(Color.BLUE).geodesic(true);
        for (int z = 0; z < list.size(); z++) {
            LatLng point = new LatLng(list.get(z).latitude, list.get(z).longitude);
            options.add(point);
        }
        polyline = gMap.addPolyline(options);
    }
}