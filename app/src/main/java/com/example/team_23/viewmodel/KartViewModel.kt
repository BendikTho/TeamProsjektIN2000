package com.example.team_23.viewmodel

import android.location.Location
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.team_23.model.MainRepository
import com.example.team_23.model.api.map_dataclasses.Routes
import com.example.team_23.model.api.metalerts_dataclasses.Alert
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class KartViewModel(private val repo: MainRepository): ViewModel() {
    val alerts = MutableLiveData<MutableList<Alert>>()
    val routes = MutableLiveData<List<Routes>>()             // Liste med responsen fra api-kall til Directions API
    val path = MutableLiveData<MutableList<List<LatLng>>>()  // Liste som inneholder polyline-punktene fra routes (sørg for at hele tiden samsvarer med 'routes')
    var location = MutableLiveData<Location>()               // Enhetens lokasjon (GPS)
    var alertAtPosition = MutableLiveData<Alert>()

    /* Grensesnitt til View.
    * Henter alle tilgjengelige varsler.
     */


    /* Grensesnitt til View.
    * Henter varsler for nåværende sted.
    * Er avhengig av at lokasjon (livedata 'location') er tilgjengelig og oppdatert.
    */
    fun getAlertsCurrentLocation() {


        /*val lat = location.value?.latitude
        val lon = location.value?.longitude
        // Feilsjekking i tilfelle ikke lokasjon tilgjengelig?
        if (lat == null || lon == null) {
            Log.w("KartViewModel", "Advarsel: getAlertsCurrentLocation() ble kalt men lokasjon er ikke tilgjengelig.")
        }
        getAlerts(lat, lon)*/
    }

    fun getAlert(lat: Double, lon: Double){
        CoroutineScope(Dispatchers.Default).launch {
            var alert: Alert? = null
            val RSSitem = repo.getRssFeed(lat, lon)
            if (RSSitem != null) {
                Log.d("Alert", RSSitem.size.toString())
                alert = repo.getCapAlert(RSSitem[0].link!!)
            }
            if (alert != null){
                alertAtPosition.postValue(alert)
            }



        }

    }

    /* Grensesnitt til View
     * Returnerer en instans av livedata-instans med lokasjon.
     */
    fun getLocation(): LiveData<Location> {
        updateLocation()
        Log.d("KartViewModel", "getLocation: ${location.value?.latitude}, ${location.value?.longitude}")
        return location
    }

    fun getAlertsForRoute() {

    }

    fun findRoute() {
        // Kaller på Directions API fra Google (via Repository) og oppdaterer routes-LiveData
        CoroutineScope(Dispatchers.Default).launch {
            val routesFromApi = repo.getRoutes()
            if (routesFromApi != null) {
                routes.postValue(routesFromApi)                 // Oppdater routes (hentet fra API)
                path.postValue(getPolylinePoints(routes.value)) // Oppdater path (lat/lng-punkter) basert på ny rute
                Log.d("KartViewModel.findRoute", "Path oppdatert")
            }
        }
    }

    fun showBonfireSpots() {

    }

    // Oppdaterer nåværende posisjon ved kall til repository
    private fun updateLocation() {
        location = repo.getLocation() as MutableLiveData<Location>
    }

    /* Privat metode, implementerer funksjonaliteten brukt i getAllAlerts() og getAlertsCurrentLocation().
     * Dersom ingen lokasjon oppgis (parametre er null) hentes alle tilgjengelige varsler.
     * Ellers hentes varsler for angitt posisjon.
     * Metoden oppdaterer alerts-variabelen (LiveData).
    */
    fun getAllAlerts() {

        val varselListe = mutableListOf<Alert>()
        val varselListeMutex = Mutex()  // Lås til varselListe
        CoroutineScope(Dispatchers.Default).launch {
            val rssItems = repo.getRssFeed(null, null)
            // For hvert RssItem gjøres et API-kall til den angitte lenken hvor varselet kan hentes fra.
            // Hvert kall gjøres med en egen Coroutine slik at varslene hentes samtidig. Ellers må hvert
            // API-kall vente i tur og orden på at det forrige skal bli ferdig, noe som er tidkrevende.
            // Bruker Mutex for å sikre at ingen av trådene skriver til varselListe samtidig (unngå mulig Race Condition).
            rssItems?.forEach {  // For hvert rssItem (løkke)
                withContext(Dispatchers.Default) {          // dispatch med ny coroutine for hvert rssItem
                    val alert = repo.getCapAlert(it.link!!) // Hent CAP-alert via repository. 'it': rssItem
                    if (alert != null) {
                        varselListeMutex.withLock { // Trådsikkert: ingen tråder modifiserer listen samtidig
                            varselListe.add(alert)
                        }
                    }
                }
            }
            alerts.postValue(varselListe)
        }
    }

    /* Hjelpemetode for findRoute()
     * Må gå gjennom dataklasse for dataklasse (base, legs, steps og polyline)
     * for å få tak i informasjonen programmet trenger (points i polyline) for å lage rute på kartet
     */
    private fun getPolylinePoints(routes: List<Routes>?): MutableList<List<LatLng>> {
        // tmpPathList: Brukt for å konstruere hele polyline-listen, før LiveDataen oppdateres med den komplette listen.
        val tmpPathList = mutableListOf<List<LatLng>>()
        val TAG = "Polyline Points"
        Log.d(TAG, "Antall routes: ${routes?.size}")
        if (routes != null) {
            for (route in routes) {  // Sårbart for bugs, mutable data kan ha blitt endret. TODO: finne bedre løsning
                val legs = route.legs
                Log.d(TAG, "Antall legs (i route.legs): ${legs?.size}")
                if (legs != null) {
                    for (leg in legs) {
                        val steps = leg.steps
                        Log.d(TAG, "Antall steps (i leg.steps): ${steps?.size}")
                        if (steps != null) {
                            for (step in steps) {
                                val points = step.polyline?.points
                                tmpPathList.add(PolyUtil.decode(points))
                            }
                        }
                    }
                }
            }
        }
        return tmpPathList
    }
}