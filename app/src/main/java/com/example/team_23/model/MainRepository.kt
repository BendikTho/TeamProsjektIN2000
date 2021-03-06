package com.example.team_23.model

import android.location.Location
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.team_23.model.api.ApiServiceImpl
import com.example.team_23.model.api.CapParser
import com.example.team_23.model.api.MetAlertsRssParser
import com.example.team_23.model.dataclasses.*
import com.example.team_23.model.dataclasses.metalerts_dataclasses.Alert
import com.example.team_23.model.dataclasses.metalerts_dataclasses.RssItem
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.util.*

class MainRepository(private val apiService: ApiServiceImpl, private val fusedLocationProviderClient: FusedLocationProviderClient) {
    private val tag = "MainRepository"

    private val gson = Gson()

    // API-Dokumentasjon: https://in2000-apiproxy.ifi.uio.no/weatherapi/metalerts/1.1/documentation
    private val endpoint = "https://in2000-apiproxy.ifi.uio.no/weatherapi/metalerts/1.1/"
    // Options i permanentOptions-listen blir med i alle API-kall til MetAlerts.
    private val permanentOptions = listOf("event=forestFire")

    // Directions API
    private val directionsUrlOrigin = "https://maps.googleapis.com/maps/api/directions/json?origin="
    private val directionsUrlDestination = "&destination="
    private val mode = "&mode=walking"
    private val directionsUrlKey = "&key=AIzaSyAyK0NkgPMxOOTnWR5EFKdy2DzfDXGh-HI"

    //Places API
    private val placesUrlStart = "https://maps.googleapis.com/maps/api/place/findplacefromtext/json?input="
    private val placesUrlEnd = "&inputtype=textquery&fields=formatted_address,name,geometry&key=AIzaSyAyK0NkgPMxOOTnWR5EFKdy2DzfDXGh-HI"

    //Geocode API
    private val placesURL = "https://maps.googleapis.com/maps/api/geocode/json?"
    private val key = "AIzaSyAyK0NkgPMxOOTnWR5EFKdy2DzfDXGh-HI"

    /* Henter stedsnavn fra Geocode API basert p?? lokasjon (LatLng)
    * API-responsens parses til dataklasser av Gson
    */
    suspend fun getPlaceNameFromLocation(latlng: LatLng): String? {
        Log.d(tag, "Soker etter sted fra Geocode API")
        val geocodePath = "${placesURL}latlng=${latlng.latitude},${latlng.longitude}&key=${key}"
        var placeName: String? = null
        try {
            val httpResponse = apiService.fetchData(geocodePath) ?: throw IOException()
            Log.d(tag, "Fikk respons fra Geocode API. Respons-streng (delstreng): ${httpResponse.subSequence(0, 30)}")
            val parsedResponse: GeocodeBase = gson.fromJson(httpResponse, GeocodeBase::class.java)
            if (parsedResponse.results != null && parsedResponse.results.isNotEmpty()) {
                val addressComponentList = parsedResponse.results[1].address_components
                if (addressComponentList != null && addressComponentList.size > 2)
                    placeName = addressComponentList[1].long_name  // Mangler sjekk for antall returnerte resultater
            }
        } catch (exception: IOException) {
            Log.w(tag, "Feil under henting av types til sted: ${exception.message}")
        }
        return placeName
    }

    /* Returnerer lokasjon for angitt stedsnavn. Brukes blant annet til s??kefunksjonen.
    * API-responsens parses til dataklasser av Gson
    */
    suspend fun getLocationFromPlacename(place: String): List<Candidates>?{
        Log.d(tag, "Soker etter sted fra Google!")
        var places: List<Candidates>? = null
        val placesPath = "${placesUrlStart}${place}${placesUrlEnd}"
        try {
            val httpResponse = apiService.fetchData(placesPath) ?: throw IOException()
            Log.d(tag, "Fikk respons fra Geocode API. Respons-streng: ${httpResponse.subSequence(0, 30)}")
            val response = gson.fromJson(httpResponse, MainBase::class.java)
            places = response.candidates
        } catch (exception: IOException) {
            Log.w(tag, "Feil under henting av latlng til sted: ${exception.message}")
        }
        return places
    }

    // Henter Json fra Direction API (Google) og parser ved hjelp av Gson til dataklasser.
    suspend fun getRoutes(origin_lat : Double?, origin_lon : Double?, destination_lat : Double?, destination_lon : Double?): List<Routes>? {
        Log.d(tag, "Henter ruter fra Google!")
        var routes: List<Routes>? = null
        val directionPath = "${directionsUrlOrigin}${origin_lat},${origin_lon}${directionsUrlDestination}${destination_lat},${destination_lon}${mode}${directionsUrlKey}"
        try {
            val httpResponse = apiService.fetchData(directionPath) ?: throw IOException()
            val response = gson.fromJson(httpResponse, DirectionsBase::class.java)
            routes = response.routes
        } catch (exception: IOException) {
            Log.w(tag, "Feil under henting av rute: ${exception.message}")
        }
        return routes
    }


    /* Henter XML-data (RSS-feed) fra MetAlerts-proxyen (IN2000) og parser responsen.
     * @return liste med RssItem eller null
     * Suppresser varsel fra Kotlin kallet til parseren tolkes som blokkerende (IO) selv om det ikke er det.
     */
    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun getRssFeed(lat: Double?, lon: Double?) : List<RssItem>? {
        val options = permanentOptions.toMutableList()
        if (lat != null && lon != null) {
            // For ?? sikre at desimaltall skrives med punktum (og ikke komma) brukes Locale.US.
            // kotlin.format bruker enhetens Locale som default. F.eks. ble desimaltall skrevet med
            // komma n??r systemspr??k er norsk. F??rte til feil i API-kall.
            options.add("lat=%.2f".format(Locale.US, lat))
            options.add("lon=%.2f".format(Locale.US, lon))
        }
        // Kast IO Exception dersom API-kall feiler. Usikker p?? om dette er ideellt, men en iaf
        // midlertidig l??sning for ?? sikre at httpResponse er initialisert.
        var rssItems : List<RssItem>? = null
        try {
            val httpResponse : String = apiService.fetchData(endpoint, options) ?: throw IOException()
            val bytestream = httpResponse.byteInputStream()   // Konverter streng til inputStream
            rssItems = MetAlertsRssParser().parse(bytestream)
        } catch (ex : XmlPullParserException) {
            Log.w(tag, "Feil under parsing av RSS-feed:\n$ex")
        } catch (ex: IOException) {
            Log.w(tag, "IO-feil under parsing av RSS-feed:\n$ex")
        }
        return rssItems  // Returner liste med RssItems
    }

    /* Henter XML-data (CAP Alert) fra MetAlerts-proxyen (IN2000) og parser responsen.
     * @return instans av Alert eller null.
     * Suppresser varsel fra Kotlin kallet til parseren tolkes som blokkerende (IO) selv om det ikke er det.
     */
    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun getCapAlert(url : String) : Alert? {
        var alert : Alert? = null
        try {
            val httpResponse : String = apiService.fetchData(url) ?: throw IOException()
            val bytestream = httpResponse.byteInputStream()  // Konverter streng til inputStream
            alert = CapParser().parse(bytestream)
        } catch (ex : XmlPullParserException) {
            Log.w(tag, "Feil under parsing av CAP Alert:\n$ex")
        } catch (ex: IOException) {
            Log.w(tag, "IO-feil under parsing av CAP alert:\n$ex")
        }
        return alert
    }

    /* Henter GPS-lokasjon ved hjelp av FusedLocationProviderClient
    * NB: Metoden antar at Viewet sjekker tilgangsrettigheter ("permissions") og at disse er innvilget!
    * Den kaster bare en SecurityException dersom f.eks. bruker har avsl??tt lokasjonstilgang ("permission")
    * @returns LiveData<Location>
    */
    @Throws(SecurityException::class)
    fun getLocation(): LiveData<Location?> {
        Log.d(tag, "getLocation ble kalt")
        val liveDataLocation = MutableLiveData<Location>()
        try {
            val locationTask = fusedLocationProviderClient.lastLocation
            locationTask.addOnSuccessListener {
                // Resultat ("it") kan v??re null dersom system ikke har informasjon om n??v??rende lokasjon.
                if (it == null) {
                    Log.d(tag, "getLocation: onSuccessListener. Resultat (Location) er null.")
                } else {
                    Log.d(tag,"getLocation: onSuccessListener. Resultat: ${it.latitude}, ${it.longitude}")
                }
                liveDataLocation.postValue(it)
            }
        } catch (ex: SecurityException) {
            Log.w(tag, "Error when getting location")
        }
        return liveDataLocation
    }

    /* Parser JSON-filen med b??lplasser og returnerer en liste med HashMaps.
    * TODO: gj??re om til asynkront Coroutine-kall (pga. fillesning)?
    */
    fun getCampfireSpots(): List<Campfire> {
        // Gj??r det slik som dette for ?? unng?? ?? gi Context som parameter.
        // Men usikker p?? om dette f??r utilsiktede konsekvenser.
        val file = "res/raw/campfire_spots.json"
        val bonfireSpotsStream = this.javaClass.classLoader?.getResourceAsStream(file)
        val jsonString = bonfireSpotsStream?.bufferedReader()?.readText()
        bonfireSpotsStream?.close()
        Log.d(tag, "getBonfireSpots: henter b??lplassoversikt fra res/raw. Returnert jsonString er ikke null eller tom: ${! jsonString.isNullOrEmpty()}")
        /* Parse json-streng til b??lplass-objekter */
        val bType = object: TypeToken<List<Campfire>>() {}.type
        var bonfireList = mutableListOf<Campfire>()
        if (jsonString != null)
            bonfireList = gson.fromJson(jsonString, bType)
        return bonfireList
    }
}