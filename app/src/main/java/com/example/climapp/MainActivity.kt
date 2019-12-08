package com.example.climapp

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.pm.PackageManager
import android.graphics.drawable.AnimationDrawable
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.climapp.adapter.PrevisaoAdapter
import com.example.climapp.model.Clima
import com.example.climapp.model.Previsao
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    val PERMISSION_ID = 42
    var latitude: Double = 0.0
    var longitude: Double = 0.0
    var listaPrevisoes = ArrayList<Previsao>()
    lateinit var dialog: ProgressDialog
    lateinit var url: String
    lateinit var queue: RequestQueue
    lateinit var mFusedLocationClient: FusedLocationProviderClient


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        val animDrawable = root_layout.background as AnimationDrawable
        animDrawable.setEnterFadeDuration(10)
        animDrawable.setExitFadeDuration(5000)
        animDrawable.start()

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        queue = Volley.newRequestQueue(this)
        url = "https://api.hgbrasil.com/weather?key=4e56cf83&lat=${latitude}&log=${longitude}&user_ip=remote"

        dialog = ProgressDialog(this)
        dialog.setTitle("Trabalhando")
        dialog.setMessage("Recureando informações do Clima, aguarde...")
        dialog.show()

        if (checkPermissions(
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
        ) {
            getLastLocation()
        }
    }

    private fun checkPermissions(vararg permission: String): Boolean {
        val mensagemPermissao =
            "A localização é necessária para que possamos solicitar" +
                    " a previsão de clima em sua localidade."
        val havePermission = permission.toList().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!havePermission) {
            if (permission.toList().any {
                    ActivityCompat.shouldShowRequestPermissionRationale(this, it)
                }) {
                val alertDialog = AlertDialog.Builder(this)
                    .setTitle("Permission")
                    .setMessage(mensagemPermissao)
                    .setPositiveButton("Ok") { id, v ->
                        run {
                            ActivityCompat.requestPermissions(this, permission, PERMISSION_ID)
                        }
                    }
                    .setNegativeButton("No") { id, v -> }
                    .create()
                alertDialog.show()
            } else {
                ActivityCompat.requestPermissions(this, permission, PERMISSION_ID)
            }
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_ID -> {
                Log.d("PERMISSION", " - Concedida")
                getLastLocation()
            }
            else -> Log.d("PERMISSION: ", " - Negada")
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLastLocation() {
        mFusedLocationClient?.lastLocation?.addOnSuccessListener { location ->
            if (location == null) {
                Log.e("LOCATION: ", "Erro ao obter Localização: ")
            } else {
                location.apply {
                    Log.d("LOCATION: ", location.toString())
                    var lat = location.latitude
                    var lon = location.longitude

                    Log.d("LOCATION - LATITUDE: ", lat.toString())
                    Log.d("LOCATION - LONGITUDE: ", lon.toString())

                    url = "https://api.hgbrasil.com/weather?key=4e56cf83&lat=${lat}&log=${lon}&user_ip=remote"

                    Log.d("GETLASTLOCATION: ", url)
                    requestWheater(url)
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun requestWheater(url: String): StringRequest {

        dialog.show()
        val stringRequest = StringRequest(Request.Method.GET, url, Response.Listener<String> { result ->
            val jsonResult = JSONObject(result).getJSONObject("results")
            val jsonPresisoesList = jsonResult.getJSONArray("forecast")
            val clima = preencheClima(jsonResult, listaPrevisoes)
            preenchePrevisoes(jsonPresisoesList)

            textViewTemperatura.text = "${clima.temperatura.toString()}˚"
            textViewHora.text = clima.hora
            textViewData.text = clima.data
            textViewMaxima.text = (clima.previsoes as ArrayList<Previsao>)[0].maxima
            textViewMinima.text = (clima.previsoes as ArrayList<Previsao>)[0].minima
            textViewTempoCelula.text = clima.descricao
            textViewNascerDoSol.text = clima.nascerDoSol
            textViewPorDoSol.text = clima.porDoSol
            textViewData.text =
                (clima.previsoes as ArrayList<Previsao>)[0].diaDaSemana?.toUpperCase()
                    .plus(" ").plus(clima.data)

            imageViewIcon.setImageResource(R.drawable.snow)
            when (clima.condicaoDoTempo) {
                "storm" -> imageViewIcon.setImageResource(R.drawable.storm)
                "snow" -> imageViewIcon.setImageResource(R.drawable.snow)
                "rain" -> imageViewIcon.setImageResource(R.drawable.rain)
                "fog" -> imageViewIcon.setImageResource(R.drawable.fog)
                "clear_day" -> imageViewIcon.setImageResource(R.drawable.sun)
                "clear_night" -> imageViewIcon.setImageResource(R.drawable.moon)
                "cloud" -> imageViewIcon.setImageResource(R.drawable.cloudy)
                "cloudly_day" -> imageViewIcon.setImageResource(R.drawable.cloud_day)
                "cloudly_night" -> imageViewIcon.setImageResource(R.drawable.cloudy_night)
            }
            val adapter = PrevisaoAdapter(applicationContext, listaPrevisoes)
            listViewPrivisoes.adapter = adapter
            adapter.notifyDataSetChanged()

            dialog.dismiss()

            Log.d("RESPONSE: ", result.toString())
        }, Response.ErrorListener {
            Log.e("ERROR: ", it.localizedMessage)
        })
        queue.add(stringRequest)
        return stringRequest
    }

    private fun preencheClima(jsonObject: JSONObject, listaPrevisoes: ArrayList<Previsao>): Clima {
        val clima = Clima(
            jsonObject.getInt("temp"),
            jsonObject.getString("date"),
            jsonObject.getString("time"),
            jsonObject.getString("condition_code"),
            jsonObject.getString("description"),
            jsonObject.getString("currently"),
            jsonObject.getString("cid"),
            jsonObject.getString("city"),
            jsonObject.getString("img_id"),
            jsonObject.getInt("humidity"),
            jsonObject.getString("wind_speedy"),
            jsonObject.getString("sunrise"),
            jsonObject.getString("sunset"),
            jsonObject.getString("condition_slug"),
            jsonObject.getString("city_name")
        )
        clima.previsoes = listaPrevisoes
        return clima
    }

    private fun preenchePrevisoes(previsoes: JSONArray) {
        for (i in 0 until previsoes.length()) {
            val previsaoObject = previsoes.getJSONObject(i)
            val previsao = Previsao(
                previsaoObject.getString("date"),
                previsaoObject.getString("weekday"),
                previsaoObject.getString("max"),
                previsaoObject.getString("min"),
                previsaoObject.getString("description"),
                previsaoObject.getString("condition")
            )
            listaPrevisoes.add(previsao)
        }
    }
}
