package com.example.team_23.viewmodel

import android.util.Log
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
    val varsler = MutableLiveData<MutableList<Alert>>()

    // Liste med responsen fra api-kall til Directions API
    val routes = MutableLiveData<List<Routes>>()
    // Liste som inneholder polyline-punktene fra routes (sørg for at hele tiden samsvarer med 'routes')
    val path = MutableLiveData<MutableList<List<LatLng>>>()

    fun hentAlleVarsler() {
        val varselListe = mutableListOf<Alert>()
        val varselListeMutex = Mutex()  // Lås til varselListe

        CoroutineScope(Dispatchers.Default).launch {
            val rssItems = repo.getRssFeed()

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
            varsler.postValue(varselListe)
        }
    }

    fun hentVarslerForSted(lat: String, lon: String) {

    }

    fun findRoute() {
        // Kaller på Directions API fra Google (via Repository) oppdaterer routes-LiveData
        CoroutineScope(Dispatchers.Default).launch {
            val routesFromApi = repo.getRoutes()
            if (routesFromApi != null) {
                routes.postValue(routesFromApi)                 // Oppdater routes (hentet fra API)
                path.postValue(getPolylinePoints(routes.value)) // Oppdater path (lat/lng-punkter) basert på ny rute
                Log.d("KartViewModel.findRoute", "Path oppdatert")
            }
        }

    }

    fun hentPos() {

    }

    fun hentVarselForAngittRute() {

    }

    fun visBaalplasser() {

    }

    // Hjelpemetode for findRoute()
    // Må gå gjennom dataklasse for dataklasse (base, legs, steps og polyline)
    // for å få tak i informasjonen programmet trenger (points i polyline) for å lage rute på kartet
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