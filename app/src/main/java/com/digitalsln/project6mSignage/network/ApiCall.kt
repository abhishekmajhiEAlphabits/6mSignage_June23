package com.digitalsln.project6mSignage.network

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.digitalsln.project6mSignage.receivers.DisplayOverlayReceiver
import com.digitalsln.project6mSignage.MainActivity
import com.digitalsln.project6mSignage.model.TimeData
import com.digitalsln.project6mSignage.tvLauncher.utilities.AppPreference
import com.digitalsln.project6mSignage.tvLauncher.utilities.Constants
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.*
import javax.inject.Singleton

@Singleton
class ApiCall(context: Context) {
    private val TAG = "TvTimer"
    val context = context
    fun callApi() {
        var localScreenCode = AppPreference(context).retrieveLocalScreenCode(
            Constants.localScreenCode,
            Constants.defaultLocalScreenCode
        )

        Toast.makeText(context,"Screen Code From App Preference :: $localScreenCode",Toast.LENGTH_LONG).show()
        ApiClient.client().create(ApiInterface::class.java)
            .getTime(localScreenCode).enqueue(object : Callback<List<TimeData>> {
                @RequiresApi(Build.VERSION_CODES.O)
                override fun onResponse(
                    call: Call<List<TimeData>>,
                    response: Response<List<TimeData>>
                ) {
                    if (response.isSuccessful) {
                        Toast.makeText(context,""+response,Toast.LENGTH_LONG).show();

                        if( response.body()!=null){
                            val calendar = Calendar.getInstance()
                            val day = calendar.get(Calendar.DAY_OF_WEEK) - 1
                            var fromTime  = ""
                            var toTime = ""

                            if(response.body()!![day]!=null && response.body()!![day].from!=null){
                                toTime = response.body()!![day].from;
                            }

                            if(response.body()!![day]!=null && response.body()!![day].to!=null){
                                fromTime = response.body()!![day].to;
                            }

                            if(fromTime.isNotEmpty() && toTime.isNotEmpty()){
                                AppPreference(context).saveFromTime(
                                    fromTime,
                                    Constants.fromTime
                                )
                                AppPreference(context).saveToTime(
                                    toTime,
                                    Constants.toTime
                                )
                                Toast.makeText(
                                    context,
                                    "fromTime : $fromTime & toTime : $toTime",
                                    Toast.LENGTH_LONG
                                ).show()
                                Log.d(TAG, "${response.body()}")
                                /* if api call is successful then alarm manager for screen off/on is called */
                                lockTV()
                            }

                        }

                    } else {
                        Toast.makeText(
                            context,
                            "Failed api call",
                            Toast.LENGTH_SHORT
                        ).show()
                        Log.d(TAG, "Failed")
                    }
                }

                override fun onFailure(call: Call<List<TimeData>>, t: Throwable) {
                    Log.d(TAG, "$t")
                }
            })
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("InvalidWakeLockTag", "ShortAlarm")
    private fun lockTV() {
        try {
            if (MainActivity.wakeLock.isHeld) {
                Log.d(TAG, "${MainActivity.wakeLock.isHeld}")
                MainActivity.wakeLock.release()
            }
            MainActivity.isTimerSet = true
            /* initializes and schedules alarm manager for performing screen off and on */
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val i = Intent(context, DisplayOverlayReceiver::class.java)
            val pi = PendingIntent.getBroadcast(context, 0, i, 0);




            val futureDate: Calendar = Calendar.getInstance()
            val fromTime =
                AppPreference(context).retrieveFromTime(
                    Constants.fromTime,
                    Constants.defaultFromTime
                )
            val cal = Calendar.getInstance()
            val sdf = SimpleDateFormat("HH:mm:ss")
            val date: Date = sdf.parse(fromTime) //give the fromTime here
            cal.time = date
            /* gets the time from api and compares it with system current time */
            val apiTime =
                cal[Calendar.HOUR_OF_DAY] * 3600 + cal[Calendar.MINUTE] * 60 + cal[Calendar.SECOND]
            val systemCurrentTime =
                futureDate[Calendar.HOUR_OF_DAY] * 3600 + futureDate[Calendar.MINUTE] * 60 + futureDate[Calendar.SECOND]
            Log.d(
                TAG, "$apiTime :: $systemCurrentTime"
            )
            /* if time from api is greater than system time then only schedules the alarm */
            if (apiTime > systemCurrentTime) {
                Log.d(
                    TAG,
                    "${cal[Calendar.HOUR_OF_DAY]} :: ${cal[Calendar.MINUTE]}:: ${cal[Calendar.SECOND]}"
                )
                futureDate.set(Calendar.HOUR_OF_DAY, cal[Calendar.HOUR_OF_DAY])
                futureDate.set(Calendar.MINUTE, cal[Calendar.MINUTE])
                futureDate.set(Calendar.SECOND, cal[Calendar.SECOND])
                am.setExact(AlarmManager.RTC_WAKEUP, futureDate.time.time, pi);
            } else {
                if (!MainActivity.wakeLock.isHeld) {
                    MainActivity.wakeLock.acquire()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Permissions not granted",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}