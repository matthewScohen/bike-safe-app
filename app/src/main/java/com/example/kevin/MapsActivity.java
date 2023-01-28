package com.example.kevin;

import androidx.fragment.app.FragmentActivity;

import android.content.Intent;
import android.os.Bundle;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.example.kevin.databinding.ActivityMapsBinding;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private ActivityMapsBinding binding;
    String response;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        Intent intent = getIntent();
        response = intent.getStringExtra("API_RESP");
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        JSONObject json;
        JSONArray routes;
        JSONObject overview;
        String points;
        LatLng sw, ne;
        try {
            json = new JSONObject(response);
            routes = json.getJSONArray("routes");
            overview = routes.getJSONObject(0).getJSONObject("overview_polyline");
            points = overview.getString("points");
            ne = new LatLng(Double.parseDouble(routes.getJSONObject(0).getJSONObject("bounds").getJSONObject("northeast").getString("lat")),
                            Double.parseDouble(routes.getJSONObject(0).getJSONObject("bounds").getJSONObject("northeast").getString("lng")));
            sw = new LatLng(Double.parseDouble(routes.getJSONObject(0).getJSONObject("bounds").getJSONObject("southwest").getString("lat")),
                            Double.parseDouble(routes.getJSONObject(0).getJSONObject("bounds").getJSONObject("southwest").getString("lng")));
        } catch (Exception e){
            e.printStackTrace();
            return;
        }

        List<LatLng> lat_lang = decodePoly(points);
        PolylineOptions options = new PolylineOptions().geodesic(true).addAll(lat_lang);

        Polyline line = mMap.addPolyline(options);
        line.setWidth(15);
        line.setColor(0xff0082ff);
        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(new LatLngBounds(sw, ne), 100));
//        mMap.setLatLngBoundsForCameraTarget(new LatLngBounds(sw, ne));


        // Add a marker in Sydney and move the camera
//        LatLng sydney = new LatLng(-34, 151);
//        mMap.addMarker(new MarkerOptions().position(sydney).title("Marker in Sydney"));
//        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));
    }

    //taken from @'Vikrant Shah' at https://stackoverflow.com/questions/39454857/how-to-buffer-a-polyline-in-android-or-draw-a-polygon-around-a-polyline/42664925#42664925
    private List<LatLng> decodePoly(String encoded) {

        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            LatLng p = new LatLng((((double) lat / 1E5)),
                    (((double) lng / 1E5)));
            poly.add(p);
        }
        return poly;
    }
}