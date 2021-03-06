package com.example.team_23.view

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.example.team_23.R
import com.example.team_23.model.dataclasses.Campfire
import com.example.team_23.model.dataclasses.metalerts_dataclasses.Alert
import com.example.team_23.model.dataclasses.metalerts_dataclasses.AlertColors
import com.example.team_23.utils.ViewModelProvider
import com.example.team_23.viewmodel.KartViewModel
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.android.material.switchmaterial.SwitchMaterial


class KartActivity : AppCompatActivity(), OnMapReadyCallback {
    private val tag = "KartActivity"

    private lateinit var mMap: GoogleMap
    private lateinit var kartViewModel: KartViewModel

    private val LOCATION_PERMISSION_REQUEST = 1  // Til lokasjonsrettigheter
    private var hasLocationAccess = false

    private var marker: Marker? = null

    // ===== VARIABLER brukt av Activity =====
    // ----- Info-boks -----
    private lateinit var infoButton: Button
    // ----- Meny -----
    private lateinit var menu: View
    private lateinit var menuButton: ImageButton
    private var menuVisible = false
    private lateinit var menuCampfireButtonShape: View
    // ----- Alert Popup-box -----
    private lateinit var popup: View
    private lateinit var popupCloseButton: ImageButton
    private var popupVisible = false
    private lateinit var warningArea: TextView
    private lateinit var warningInfo: TextView
    private lateinit var warningLevel: TextView
    private lateinit var warningLevelImg: ImageView
    private lateinit var warningLevelColor: View
    private lateinit var travelHereButtonIcon: ImageView
    private lateinit var travelHereButtonText: TextView
    // ----- Travel here -------
    private lateinit var travelHereButton: ImageButton
    private lateinit var travelPolylineList: MutableList<Polyline>
    // ----- Alert Levels Description -----
    private lateinit var alertLevelsDescButton: Button
    private lateinit var alertLevelDescCloseButtonShape: ImageButton
    private lateinit var alertLevelsDescPopup: View
    private lateinit var alertLevelsDescCloseButton: ImageButton
    private var alertLevelsDescVisible = true
    // ----- Campfire -----
    private var menuCampfireButtonIsChecked = true  // Erstatt med direkte aksess til Switch
    private val ZOOM_LEVEL_SHOW_CAMPFIRES = 8.5
    private lateinit var switchCampfireButton: SwitchMaterial
    private lateinit var campfireSpots: List<Campfire>
    private lateinit var campfireMarkers: MutableList<Marker>
    // ----- Overlay (Alerts) -----
    private lateinit var switchOverlayButton: SwitchMaterial
    private var overlayVisible = true
    private lateinit var overlayPolygonList: MutableList<Polygon>  // Listen med polygoner
    private lateinit var varslerHer: Button


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Setter KartViewModel og Kart tidlig.
        kartViewModel = ViewModelProvider.getKartViewModel(LocationServices.getFusedLocationProviderClient(applicationContext))
        // Henter SupportMapFragment og f??r beskjed n??r kartet (map) er klart til bruk. Se onMapReady-metoden.
        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // ===== SETT VIEWS =====
        // ----- Varsler Her-knapp -----
        varslerHer = findViewById(R.id.varslerHerButton)

        // ----- Meny -----
        menu = findViewById(R.id.menu)
        menuButton = findViewById(R.id.menuButton)
        val rulesActivityBtn = findViewById<Button>(R.id.menuRulesButton)  // Knapp som sender bruker til reglene
        switchCampfireButton = findViewById(R.id.switchCampfire)
        menuCampfireButtonShape = findViewById(R.id.menuCampfireButtonShape) //Brukes for ?? justere margin
        // ----- Info-boks -----
        infoButton = findViewById(R.id.menuInfoButton)

        // ----- Alert Popup-boks -----
        popup = findViewById(R.id.popup)
        popupCloseButton = findViewById(R.id.popupAlertCloseButton)
        warningArea = findViewById(R.id.popupAlertArea)
        warningInfo = findViewById(R.id.popupAlertInfoContent)
        warningLevel = findViewById(R.id.popupAlertLevelContent)
        warningLevelImg = findViewById(R.id.warningLevelImg)
        warningLevelColor = findViewById(R.id.popupAlertLevelColor)
        travelHereButton = findViewById(R.id.popupDraHitButton)
        travelHereButtonIcon = findViewById(R.id.popupDraHitButtonIcon)
        travelHereButtonText = findViewById(R.id.popupDraHitButtonText)
        // ----- Levels -----
        alertLevelsDescButton = findViewById(R.id.popupAlertDescButton)
        alertLevelsDescPopup = findViewById(R.id.levelsDesc)
        alertLevelsDescCloseButton = findViewById(R.id.levelsDescCloseButton)
        alertLevelDescCloseButtonShape = findViewById(R.id.levelsDescShape)

        // ------- Travel here -------
        travelPolylineList = mutableListOf()

        // ===== (ONCLICK) LISTENERS =====
        //Regler-knappen som viser regler til bruker
        rulesActivityBtn.setOnClickListener{
            val intent = Intent(this,RegelView::class.java)
            startActivity(intent)
            if(menuVisible){
                menu.visibility = View.GONE
                menuButton.background = ResourcesCompat.getDrawable(resources,R.drawable.menubutton,theme)
                mMap.uiSettings.isScrollGesturesEnabled = true
                menuVisible = !menuVisible
            }
        }

        // ===== (ONCLICK) LISTENERS FOR INFO =====
        //Info-knappen som viser informasjon til bruker
        infoButton.setOnClickListener {
            val intent = Intent(this,InfoView::class.java)
            startActivity(intent)
        }

        //Info knapp som endrer info sin synlighet
        infoButton.setOnClickListener{
            val intent = Intent(this,InfoView::class.java)
            startActivity(intent)
            if(menuVisible){
                menu.visibility = View.GONE
                menuButton.background = ResourcesCompat.getDrawable(resources, R.drawable.menubutton,theme)
                mMap.uiSettings.isScrollGesturesEnabled = true
                menuVisible = !menuVisible
            }
        }

        // ===== OBSERVERS =====
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, getString(R.string.apiNokkel))
        }

        // Initialize the AutocompleteSupportFragment.
        val autocompleteFragment =
            supportFragmentManager.findFragmentById(R.id.searchbarFragment)
                    as AutocompleteSupportFragment

        // Spesifiserer typen data som returneres
        autocompleteFragment.setPlaceFields(listOf(Place.Field.ID, Place.Field.NAME))

        // Setter opp en PlaceSelectionListener for ?? h??ndtere responsen
        autocompleteFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                Log.d("OnPlaceSelected", "latlng onPlaceSelected: ${place.latLng}")
                kartViewModel.findPlace(place.name!!)
                warningArea.text = place.name

                travelPolylineList.forEach{ it.remove() }   // Fjern tidligere tidligere tegnet rute fra kart.
                travelPolylineList.clear()
                Log.i("OnPlaceSelected", "Place: ${place.name}, ${place.latLng}")
                if (!popupVisible) {
                    togglePopup()
                }
            }

            //Ved feil
            override fun onError(p0: Status) {
                Log.i("OnError", "An error occurred: $p0")
            }
        })
    }

    // ===== GOOGLE MAP READY =====
    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        // ===== VARIABLER =====
        // ----- Kart -----
        mMap = googleMap
        // Setter padding p?? toppen til kartet slik at kartet ikke havner bak den ??verste fanen i appen.
        val height = Resources.getSystem().displayMetrics.heightPixels
        val width = Resources.getSystem().displayMetrics.widthPixels
        val paddingTop = height - height / 5
        val paddingRight = width / 30
        mMap.setPadding(0, paddingTop, paddingRight, 0)

        // -- B??lplasser --
        campfireMarkers = mutableListOf()  // Liste som holder p?? mark??rene

        // -- Overlay --
        overlayVisible = true
        overlayPolygonList = mutableListOf()
        switchOverlayButton = findViewById(R.id.switchOverlay)
        switchCampfireButton.isChecked = true
        switchOverlayButton.isChecked = true

        // ===== LOKASJON =====
        // Sjekk tilgang til lokasjon (skal ogs?? sette mMap.isMyLocationEnabled og oppdaterer lokasjon dersom tilgang)
        getLocationAccess()
        Log.d("KartActivity.onMapReady", "mMap.isMyLocationEnabled: ${mMap.isMyLocationEnabled}")

        // ===== OBSERVERE =====
        // Observer varsel-liste fra KartViewModel
        // NB: Denne skal brukes kun til varsel-overlay, og ikke til popup-boks
        kartViewModel.allAlerts.observe(this, { alertList ->
            Log.d(tag, "Endring observert i allAlerts-liste!")
            allAlertsObserver(alertList)
        })

        // Observer path-livedata (i fra KartViewModel), tegn polyline ved oppdatering.
        kartViewModel.path.observe(this, { paths -> drawDirectionsPath(paths) })

        //Observerer places-livedata (i fra KartViewModel), plasserer marker p?? s??kt sted ved oppdatering
        kartViewModel.place.observe(this, { places ->
            Log.d(tag, "Places: $places")

            placeMarker(places)
        })

        //Observerer alertAtPosition-livedata (i fra kartViewModel), oppdaterer informasjonsvinduet ut ifra varselet
        kartViewModel.alertAtPosition.observe(this, {
            // Observerer endringer i alertAtPosition (type LiveData<Alert>)
            val alert = it
            Log.d(tag, "Oppdatering observert i alertAtPosition. Alert: $it")
            val warningText: String
            val background: Drawable
            val colorLevel: Drawable
            if (alert != null) {
                val info = alert.infoNo
                warningArea.text = info.area.areaDesc
                warningInfo.text = info.instruction
                val alertColorLevel = alert.getAlertColor()

                when (alertColorLevel) {
                    AlertColors.YELLOW -> {
                        warningText = getString(R.string.gulSkogbrannfare)
                        background = ResourcesCompat.getDrawable(resources, R.drawable.yellowwarning, theme)!!
                        colorLevel = ResourcesCompat.getDrawable(resources, R.color.alertYellow, theme)!!
                    }
                    AlertColors.ORANGE -> {
                        warningText = getString(R.string.oransjeSkogbrannfare)
                        background = ResourcesCompat.getDrawable(resources, R.drawable.orangewarning, theme)!!
                        colorLevel = ResourcesCompat.getDrawable(resources, R.color.alertOrange, theme)!!
                    }
                    AlertColors.RED -> {
                        warningText = getString(R.string.gulSkogbrannfare)
                        background = ResourcesCompat.getDrawable(resources, R.drawable.orangewarning, theme)!!  // TODO: hent r??d varsel fra Githuben til YR!
                        colorLevel = ResourcesCompat.getDrawable(resources, R.color.alertRed, theme)!!
                        Log.w(tag, "Returnert alertColor er RED. Ikke forventet. Fortsetter kj??ring.")
                    }
                    AlertColors.UNKNOWN -> {
                        Log.w(tag, "Returnert alertColor er Unkown.")
                        warningText = "?"
                        colorLevel = ResourcesCompat.getDrawable(resources, R.color.black, theme)!!
                        background = ResourcesCompat.getDrawable(resources, R.drawable.questionmark, theme)!!
                    }
                }
            } else {
                // Ingen varsel (alert er null)
                warningText = getString(R.string.ingenVarsel)
                background = ResourcesCompat.getDrawable(resources, R.drawable.shape, theme)!!
                // Usikker p?? hvor stabil observeringen er. Oppst??r mulige race conditions?
                kartViewModel.placeName.observe(this, { placeName -> warningArea.text = placeName })
                warningInfo.text = getString(R.string.ingenVarselOmr??det)
                colorLevel = ResourcesCompat.getDrawable(resources, R.color.green, theme)!!
            }
            warningLevel.text = warningText
            warningLevelImg.background = background
            warningLevelColor.background = colorLevel
        })

        // ===== ON CLICK LISTENERS =====
        menuButton.setOnClickListener { toggleMenu() }

        alertLevelsDescButton.setOnClickListener { toggleLevelsPopup() }

        alertLevelsDescCloseButton.setOnClickListener { toggleLevelsPopup() }

        alertLevelDescCloseButtonShape.setOnClickListener { toggleLevelsPopup() }

        popupCloseButton.setOnClickListener { togglePopup() }

        varslerHer.setOnClickListener {
            getLocationAccess()  // Sjekk at vi har tilgang til lokasjon fra system.
            // 'location'-variabelen i KartViewModel f??r en ny instans av LiveData *hver gang*
            // lokasjon oppdateres. M?? derfor lage en observer for den *siste og nyeste instansen* av location.
            // Da hentes varselet f??rst n??r LiveDataen har blitt fylt med informasjon om lokasjon.
            // Ellers vil den ikke ha tilgang p?? lokasjons-informasjonen.
            // NB: Ikke ideellt hvis flere 'observere' trenger ?? observere samme instans av 'location'.
            resetContentOfAlertPopup()  // T??m innhold i varselvisning (popup)
            togglePopup()               // Vis popup
            travelHereButton.visibility = View.GONE
            travelHereButtonIcon.visibility = View.GONE
            travelHereButtonText.visibility = View.GONE
            val latestKnownLocation = kartViewModel.getLocation()  // type: LiveData<Location>
            latestKnownLocation.observe(this, {
                kartViewModel.getAlertCurrentLocation()  // Hent varsler n??r vi har lokasjon
                //kartViewModel.getAlert(it.latitude, it.longitude) // Alternativt. Disse er ekvivalente. Sikrere?
            })
        }

        switchCampfireButton.setOnCheckedChangeListener { _, isChecked -> toggleCampfires(isChecked) }
        switchOverlayButton.setOnCheckedChangeListener { _, isChecked -> toggleOverlay(isChecked) }

        mMap.setOnCameraIdleListener { toogleCampfireZoomVisibility() }

        // N??r bruker trykker p?? kartet lages det en mark??r, kan kun lages en mark??r og en rute av gangen
        mMap.setOnMapClickListener {
            marker?.remove()
            travelPolylineList.forEach { polyline -> polyline.remove() }   // Fjern tidligere tidligere tegnet rute fra kart.
            travelPolylineList.clear()

            if (!popupVisible){
                if(alertLevelsDescVisible) {
                    if(!menuVisible){
                        marker = mMap.addMarker(MarkerOptions().position(it))
                        val markerLatLng = LatLng(marker!!.position.latitude, marker!!.position.longitude)
                        resetContentOfAlertPopup()  // T??m innhold i varsel-popoup
                        togglePopup()               // Vis popup
                    }
                }
            } else{togglePopup()}
            kartViewModel.getAlert(it.latitude, it.longitude)
            if(menuVisible){
                toggleMenu()
            }
        }

        // Ved klikk p?? "Vis Min Lokasjon"-knappen (nede i h??yre hj??rne):
        // Hent en LiveData-instans med lokasjon (fra ViewModel) som deretter blir observert
        // [Denne l??sningen kan potensielt f??re til en viss delay fra knappen blir klikket til kameraet flytter seg]
        mMap.setOnMyLocationButtonClickListener {
            myLocationButtonOnClickMethod()
            true
        }

        // Ved klikk p?? "Dra hit"-knappen (i popup-menyen):
        travelHereButton.setOnClickListener{ getAndShowDirections() }

        // ===== INITALISER - API-kall, konfigurasjon ++ =====
        kartViewModel.getAllAlerts()  // Hent alle varsler ved oppstart av app, n??r kart er klart.

        // --- TEGNE B??LPLASSER ---
        drawCampfires()

        // --- FLYTT KAMERA ---
        val oslo = LatLng(59.911491, 10.757933)
        this.mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(oslo, 6f))

       mMap.setOnMarkerClickListener { // Sentrering p?? mark??r fungerer for ??yeblikket ikke
            centreMarker(it)
        }
    }

    // =========================
    // ===== HJELPEMETODER =====
    // =========================

    //Hjelpemetode for ?? sentrere kart ved klikk p?? mark??rer(b??likoner)
    private fun centreMarker(marker: Marker) : Boolean{
        val containerHeight = findViewById<RelativeLayout>(R.id.root).height
        val projection = mMap.projection
        val markerLatLng = LatLng(marker.position.latitude, marker.position.longitude)
        val markerScreenPosition: Point = projection.toScreenLocation(markerLatLng)
        val pointHalfScreenAbove = Point(markerScreenPosition.x, markerScreenPosition.y + (containerHeight / 3))

        val aboveMarkerLatLng = projection
            .fromScreenLocation(pointHalfScreenAbove)
        marker.showInfoWindow()
        val center = CameraUpdateFactory.newLatLng(aboveMarkerLatLng)
        mMap.animateCamera(center)

        return true
    }

    /* Hjelpemetode for ?? f?? tilgangsrettigheter for lokasjon */
    private fun getLocationAccess() {
        Log.d(tag, "getLocation: mMap.isMyLocationEnabled: ${mMap.isMyLocationEnabled}")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            hasLocationAccess = true
            mMap.isMyLocationEnabled = true
            kartViewModel.updateLocation()
        } else {
            Log.d(tag, "getLocation: ber om lokasjonsrettigheter")
            hasLocationAccess = false
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST)
        }
    }

    //Hjelpemetode som plasserer og f??r varselinformasjon for en mark??r p?? kartet
    private fun placeMarker(latlng: LatLng){
        marker?.remove()
        marker = mMap.addMarker(MarkerOptions().position(latlng))
        kartViewModel.getAlert(latlng.latitude, latlng.longitude)
        centreMarker(marker!!)  // Sentrering fungerer for ??yeblikket ikke
    }

    /* Metode kalles n??r svar ang. lokasjonstilgang kommer tilbake. Sjekker om tillatelse er innvilget */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.contains(PackageManager.PERMISSION_GRANTED)) {
                if (ActivityCompat.checkSelfPermission(
                                this,
                                Manifest.permission.ACCESS_FINE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED) {
                    Log.i(tag,"onRequestPermissionsResult: checkSelfPermission gir negativt svar. Har ikke tilgang.")
                } else {
                    Log.d(tag, "Lokasjonstilgang innvilget!")
                    hasLocationAccess = true
                    mMap.isMyLocationEnabled = true
                }
            } else {
                Log.i(tag, "Lokasjonsrettigheter ble ikke gitt. Appen trenger tilgang til lokasjon for enkelte funksjonaliteter")
                Toast.makeText(this, "Ikke tilgang til lokasjon.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    //toggler popup og fungerer slik at man ikke ??pner en ny popup ved ?? trykke utenfor popup/niv??/menu-vinduet
    // og at man ikke kan bevege kartet n??r de er ??pne
    private fun togglePopup() {
        travelHereButton.visibility = View.VISIBLE
        travelHereButtonIcon.visibility = View.VISIBLE
        travelHereButtonText.visibility = View.VISIBLE
        if (popupVisible) {
            popup.visibility = View.GONE
            mMap.uiSettings.isScrollGesturesEnabled = true
            if(!alertLevelsDescVisible){
                alertLevelsDescPopup.visibility = View.GONE
                alertLevelsDescVisible = !alertLevelsDescVisible
            }
            popupVisible = !popupVisible
        } else {
                popup.visibility = View.VISIBLE
                mMap.uiSettings.isScrollGesturesEnabled = false
                if (!alertLevelsDescVisible) {
                    alertLevelsDescPopup.visibility = View.GONE
                    popup.visibility = View.GONE
                    alertLevelsDescVisible = !alertLevelsDescVisible
                }
                popupVisible = !popupVisible
        }
    }

    //Hjelpemetode for ?? toggle meny-vinduet og stoppe at man kan bevege kartet mens menyen er ??pen
    private fun toggleMenu() {
        if(menuVisible) {
            menu.visibility = View.GONE
            mMap.uiSettings.isScrollGesturesEnabled = true
            menuButton.background = ResourcesCompat.getDrawable(resources, R.drawable.menubutton,theme)
        } else {
            if(popupVisible){
                togglePopup()
            }
            menu.visibility = View.VISIBLE
            mMap.uiSettings.isScrollGesturesEnabled = false
            menuButton.background = ResourcesCompat.getDrawable(resources, R.drawable.menubuttonclose,theme)
        }
        menuVisible = !menuVisible
    }

    //Hjelpemetode for ?? toggle niv??-vinduet og stoppe at man kan bevege kartet mens niv??-vinduet er ??pent
    private fun toggleLevelsPopup() {
        if (alertLevelsDescVisible){
            alertLevelsDescPopup.visibility = View.VISIBLE
            mMap.uiSettings.isScrollGesturesEnabled = false
        } else {
            alertLevelsDescPopup.visibility = View.GONE
            mMap.uiSettings.isScrollGesturesEnabled = true
        }
        alertLevelsDescVisible = !alertLevelsDescVisible
    }

    //Hjelpemetode som toggler b??likoner basert p?? om hvor mye du har zoomet p?? kartet
    private fun toogleCampfireZoomVisibility() {
        //viser kun b??likoner etter et angitt zoom-niv??
        val zoom = this.mMap.cameraPosition.zoom
        if (zoom > ZOOM_LEVEL_SHOW_CAMPFIRES && menuCampfireButtonIsChecked)
            campfireMarkers.forEach { it.isVisible = true }  // Vis b??lplasser dersom Zoom langt inne nok og visning av b??lplasser aktivert
        else
            campfireMarkers.forEach { it.isVisible = false }
    }

    //hjelpemetode for ?? toggle b??likoner
    private fun toggleCampfires(isChecked: Boolean) {
        menuCampfireButtonIsChecked = isChecked
        campfireMarkers.forEach {it.isVisible = isChecked}
        toogleCampfireZoomVisibility()
    }

    //hjelpemetode for ?? toggle farge-filterer for omr??der med skogbrannfare-varsler(overlayet)
    private fun toggleOverlay(isChecked: Boolean) {
        overlayVisible = isChecked
        overlayPolygonList.forEach {it.isVisible = isChecked}
    }

    /*
    * Metode som kalles ved oppstart og henter lagret konfigurasjon (tilstand) og setter variabler deretter.
    * (eks: skal overlay og b??lplasser vises?)
    */
    /*private fun setUpUi() {
        TODO("Implementer senere dersom vi f??r p?? plass lagring av tilstand")
    }*/

    /* Hjelpemetode som kalles n??r "MyLocation"-knapp (i kart) trykkes p?? og som sentrerer kartet p?? min lokasjon n??r knappen trykkes p?? */
    private fun myLocationButtonOnClickMethod() {
        val locationLiveData = kartViewModel.getLocation()
        // N??r LiveDataen f??r koordinater flyttes kamera til oppdatert posisjon
        locationLiveData.observe(this, {
            val location = locationLiveData.value
            if (location!= null) {
                val containerHeight = findViewById<RelativeLayout>(R.id.root).height
                val projection = mMap.projection
                val locationLatLng = LatLng(location.latitude, location.longitude)
                val markerScreenPosition: Point = projection.toScreenLocation(locationLatLng)

                val pointHalfScreenAbove = Point(
                    markerScreenPosition.x,
                    markerScreenPosition.y + (containerHeight / 3)
                )
                val aboveMarkerLatLng = projection
                   .fromScreenLocation(pointHalfScreenAbove)
                val center = CameraUpdateFactory.newLatLng(aboveMarkerLatLng)
                mMap.animateCamera(center)
            } else {
                Toast.makeText(this, "Ingen lokasjon tilgjengelig", Toast.LENGTH_SHORT).show()
            }
        })
    }

    //Hjelpemetode som tegner varselsoner i riktig farge
    private fun allAlertsObserver(alertList: List<Alert>) {
        alertList.forEach { alert ->
            val latLngList = alert.getPolygon()
            // Hent riktig farge basert p?? faregrad
            val color = when (alert.getAlertColor()) {
                AlertColors.YELLOW  -> getColor(R.color.alertYellowTransparent)
                AlertColors.ORANGE  -> getColor(R.color.alertOrangeTransparent)
                AlertColors.RED     -> getColor(R.color.alertRedTransparent)
                AlertColors.UNKNOWN -> {getColor(R.color.grey); Log.w(tag, "En feil har oppst??tt! Ukjent farge/niv?? for varsel!")}
            }
            val polygonOptions = PolygonOptions()
                    .addAll(latLngList)
                    .fillColor(color)
                    .strokeWidth(1.0f)
                    .visible(overlayVisible)
                    .clickable(false)
            // Fargelegg varsel-soner p?? kart
            val polygon = mMap.addPolygon(polygonOptions)
            overlayPolygonList.add(polygon)
        }
    }

    //hjelpemetode som henter b??lplasser og tegner de opp med b??likon i angitt st??rrelse p?? kartet
    private fun drawCampfires() {
        val campfireIconHeight = 50   // endrer stoerrelse p?? campfire ikonet
        val campfireIconWidth = 50    // -- " ---
        val campfireIcon = ContextCompat.getDrawable(this, R.drawable.campfire) as BitmapDrawable
        val smallCampfireMarkerBitmap = Bitmap.createScaledBitmap(campfireIcon.bitmap, campfireIconWidth, campfireIconHeight, false) // Brukes n??r mark??rene lages under

        campfireSpots = kartViewModel.getCampfireSpots()
        for (campfire in campfireSpots) {
            val marker = mMap.addMarker(MarkerOptions()
                    .position(LatLng(campfire.lat, campfire.lon))
                    .title("${campfire.name} (${campfire.type})")
                    .icon(BitmapDescriptorFactory.fromBitmap(smallCampfireMarkerBitmap)))

            if (marker == null)
                Log.w(tag, "onMapReady: en campfireMarker er null! Ignorerer. Kan f??re til u??nsket oppf??rsel fra app.")
            else
                campfireMarkers.add(marker)
        }
    }

    //Hjelpemetode som henter latitude og longitude for n??v??rende posisjon og et sted p?? kartet,
    //og finner en rute mellom de
    private fun getAndShowDirections() {
        travelPolylineList.forEach{ it.remove() }   // Fjern tidligere tidligere tegnet rute fra kart.
        travelPolylineList.clear()                  // Nullstill liste

        togglePopup()
        getLocationAccess()

        if (hasLocationAccess) {
            val currentLocation = kartViewModel.getLocation()
            currentLocation.observe(this, {
                Log.d(tag, "getAndShowDirections: endring observert i lokasjon")
                val originLat = it?.latitude
                val originLon = it?.longitude
                val destinationLat = marker?.position?.latitude
                val destinationLon = marker?.position?.longitude
                Log.d(tag, "getAndShowDirections: originLat: $originLat, originLon: $originLon, destinationLat: $destinationLat, destinationLon: $destinationLon")
                if (originLat == null || originLon == null || destinationLat == null || destinationLon == null) {
                    Log.w(tag,"getAndShowDirections: minst en av posisjonsverdiene er null. Kan ikke hente Directions")
                    Toast.makeText(this, "Feil: mangler posisjon", Toast.LENGTH_SHORT).show()
                } else {
                    // Har tilgang til alle posisjoner: hent rute
                    Log.d(tag, "getAndShowDirections: kaller p?? kartViewModel.findRoute()")
                    kartViewModel.findRoute(originLat, originLon, destinationLat, destinationLon)
                }
            })
        }
    }

    //Hjelpemetode som tegner en rute mellom to steder p?? kartet
    private fun drawDirectionsPath(paths: MutableList<List<LatLng>>) {
        if (paths.size == 0) {
            Toast.makeText(this, "Fant ingen rute", Toast.LENGTH_SHORT).show()
        }
        //g??r gjennom punktene i polyline for ?? skrive det ut til kartet.
        for (i in 0 until paths.size) {
            val polylineOptions = PolylineOptions().addAll(paths[i]).color(Color.RED)
            val polyline = this.mMap.addPolyline(polylineOptions)
            travelPolylineList.add(polyline)
            Log.d(tag, "drawDirectionsPath: travelPolylineList: $polyline")
        }
    }

    // T??mmer innholdet i alert-popuen.
    private fun resetContentOfAlertPopup() {
        warningArea.text = ""
        warningInfo.text = ""
        val colorLevel = ResourcesCompat.getDrawable(resources, R.color.black, theme)
        val background = ResourcesCompat.getDrawable(resources, R.drawable.questionmark, theme)
        warningLevel.text = ""
        warningLevelImg.background = background
        warningLevelColor.background = colorLevel
    }
}