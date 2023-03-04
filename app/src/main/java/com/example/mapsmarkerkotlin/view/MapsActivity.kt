package com.example.mapsmarkerkotlin.view

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.room.Room
import com.example.mapsmarkerkotlin.R

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.example.mapsmarkerkotlin.databinding.ActivityMapsBinding
import com.example.mapsmarkerkotlin.model.Place
import com.example.mapsmarkerkotlin.roomdb.PlaceDao
import com.example.mapsmarkerkotlin.roomdb.PlaceDatabase
import com.google.android.material.snackbar.Snackbar
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers

class MapsActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMapLongClickListener {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private lateinit var locationManager: LocationManager
    private lateinit var locationListener: LocationListener
    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    private lateinit var sharedPreferences: SharedPreferences
    private var trackBoolean: Boolean?=null
    private lateinit var placeDao: PlaceDao
    private lateinit var db: PlaceDatabase
    private lateinit var selectedLatLng: LatLng
    private val compositeDisposable=CompositeDisposable()
    private var selectedPlace : Place?=null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        registerLauncher()
        binding.save.isEnabled=false

        trackBoolean=false
        sharedPreferences=this.getSharedPreferences("com.example.mapsmarkerkotlin", MODE_PRIVATE)
        selectedLatLng= LatLng(0.0,0.0)

        db= Room.databaseBuilder(this@MapsActivity,PlaceDatabase::class.java,"Places")
            //.allowMainThreadQueries()
            .build()
        placeDao=db.placeDao()
    }


    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setOnMapLongClickListener(this@MapsActivity)

        val intent=intent
        val info=intent.getStringExtra("info")

        if (info.equals("new")){

            //ADD NEW PLACE

            binding.save.visibility=View.VISIBLE
            binding.delete.visibility=View.GONE

            locationManager=this.getSystemService(LOCATION_SERVICE) as LocationManager //casting
            locationListener=object : LocationListener{
                override fun onLocationChanged(location: Location) {

                    trackBoolean=sharedPreferences.getBoolean("trackBoolean",false)
                    if (!trackBoolean!!){
                        val userLocation=LatLng(location.latitude,location.longitude)
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation,15f))
                        sharedPreferences.edit().putBoolean("trackBoolean",true).apply()
                    }

                }
            }

            requestPermission()


        } else {

            //PLACE of RECYCLERVIEW

            mMap.clear()
            selectedPlace=intent.getSerializableExtra("selectedPlace") as? Place // casting

            selectedPlace?.let { // !=null

                val latLng=LatLng(it.latitude,it.longitude)
                mMap.addMarker(MarkerOptions().position(latLng).title(it.name))
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng,15f))

                binding.placeName.setText(it.name)
                binding.save.visibility=View.GONE
                binding.delete.visibility=View.VISIBLE

            }

        }


    }

    private fun requestPermission(){

        if(ContextCompat.checkSelfPermission(this,android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            if(ActivityCompat.shouldShowRequestPermissionRationale(this@MapsActivity,android.Manifest.permission.ACCESS_FINE_LOCATION)){
                //rationale
                Snackbar.make(binding.root,"Permission needed for location",Snackbar.LENGTH_INDEFINITE).setAction("Give Permission", View.OnClickListener {
                    //request permission
                    permissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)

                }).show()

            } else {
                //request permission
                permissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)

            }

        } else {
            //permission granted
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,10f,locationListener)
            val lastLocation=locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (lastLocation!=null){
                val userLastLocation=LatLng(lastLocation.latitude,lastLocation.longitude)
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLastLocation,15f))
            }
            mMap.isMyLocationEnabled=true
        }

    }

    private fun registerLauncher(){

        permissionLauncher=registerForActivityResult(ActivityResultContracts.RequestPermission()){ result ->
            if(result){
                if (ContextCompat.checkSelfPermission(this,android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
                    //permission granted
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,10f,locationListener)
                    val lastLocation=locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    if (lastLocation!=null){
                        val userLastLocation=LatLng(lastLocation.latitude,lastLocation.longitude)
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLastLocation,15f))
                    }
                    mMap.isMyLocationEnabled=true

                } else {
                    //permission denied
                    Toast.makeText(this,"Permission Needed",Toast.LENGTH_LONG).show()
                }
            }
        }

    }

    override fun onMapLongClick(p0: LatLng) {

        binding.save.isEnabled=true

        mMap.clear()
        mMap.addMarker(MarkerOptions().position(p0))
        selectedLatLng= LatLng(p0.latitude,p0.longitude)

    }

    fun save(view: View){

        // Main Thread UI, Default -> CPU, IO Thread Internet/Database

        if (selectedLatLng!=null){
            val place= Place(binding.placeName.text.toString(),selectedLatLng.latitude,selectedLatLng.longitude)

            compositeDisposable.add(
                placeDao.insert(place)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(this::handleResponse)
            )

        }

    }

    private fun handleResponse(){
        val intent=Intent(this@MapsActivity,MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }

    fun delete(view: View){

        selectedPlace?.let {

            compositeDisposable.add(
                placeDao.delete(it)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(this::handleResponse)
            )

        }

    }

    override fun onDestroy() {
        super.onDestroy()
        compositeDisposable.clear()
    }

}