package com.michatec.radio.search

import android.content.Context
import android.util.Log
import com.android.volley.AuthFailureError
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.RetryPolicy
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.Volley
import com.google.gson.GsonBuilder
import com.michatec.radio.Keys
import com.michatec.radio.helpers.NetworkHelper
import com.michatec.radio.helpers.PreferencesHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.json.JSONArray


/*
 * RadioBrowserSearch class
 */
class RadioBrowserSearch(private var radioBrowserSearchListener: RadioBrowserSearchListener) {


    /* Define log tag */
    private val TAG: String = RadioBrowserSearch::class.java.simpleName


    /* Interface used to send back search results */
    interface RadioBrowserSearchListener {
        fun onRadioBrowserSearchResults(results: Array<RadioBrowserResult>) {
        }
    }


    /* Main class variables */
    private var radioBrowserApi: String
    private lateinit var requestQueue: RequestQueue


    /* Init constructor */
    init {
        // get address of radio-browser.info api and update it in background
        radioBrowserApi = PreferencesHelper.loadRadioBrowserApiAddress()
        updateRadioBrowserApi()
    }


    /* Searches station(s) on radio-browser.info */
    fun searchStation(context: Context, query: String, searchType: Int) {
        Log.v(TAG, "Search - Querying $radioBrowserApi for: $query")

        // create queue and request
        requestQueue = Volley.newRequestQueue(context)
        val requestUrl: String = when (searchType) {
            // CASE: single station search - by radio browser UUID
            Keys.SEARCH_TYPE_BY_UUID -> "https://${radioBrowserApi}/json/stations/byuuid/${query}"
            // CASE: multiple results search by search term
            else -> "https://${radioBrowserApi}/json/stations/search?name=${query.replace(" ", "+")}"
        }

        // request data from request URL
        val stringRequest = object: JsonArrayRequest(Method.GET, requestUrl, null, responseListener, errorListener) {
            @Throws(AuthFailureError::class)
            override fun getHeaders(): Map<String, String> {
                val params = HashMap<String, String>()
                params["User-Agent"] = Keys.USER_AGENT
                return params
            }
        }

        // override retry policy
        stringRequest.retryPolicy = object : RetryPolicy {
            override fun getCurrentTimeout(): Int {
                return 30000
            }

            override fun getCurrentRetryCount(): Int {
                return 30000
            }

            @Throws(VolleyError::class)
            override fun retry(error: VolleyError) {
                Log.w(TAG, "Error: $error")
            }
        }

        // add to RequestQueue.
        requestQueue.add(stringRequest)
    }


    fun stopSearchRequest() {
        if (this::requestQueue.isInitialized) {
            requestQueue.stop()
        }
    }


    /* Converts search result JSON string */
    private fun createRadioBrowserResult(result: String): Array<RadioBrowserResult> {
        val gsonBuilder = GsonBuilder()
        gsonBuilder.setDateFormat("M/d/yy hh:mm a")
        val gson = gsonBuilder.create()
        return gson.fromJson(result, Array<RadioBrowserResult>::class.java)
    }


    /* Updates the address of the radio-browser.info api */
    private fun updateRadioBrowserApi() {
        CoroutineScope(IO).launch {
            val deferred: Deferred<String> = async { NetworkHelper.getRadioBrowserServerSuspended() }
            radioBrowserApi = deferred.await()
        }
    }


    /* Listens for (positive) server responses to search requests */
    private val responseListener: Response.Listener<JSONArray> = Response.Listener { response ->
        if (response != null) {
            radioBrowserSearchListener.onRadioBrowserSearchResults(createRadioBrowserResult(response.toString()))
        }
    }


    /* Listens for error response from server */
    private val errorListener: Response.ErrorListener = Response.ErrorListener { error ->
        Log.w(TAG, "Error: $error")
    }

}