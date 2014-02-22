package com.mridang.location;

import java.util.List;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import com.bugsense.trace.BugSenseHandler;
import com.google.android.apps.dashclock.api.DashClockExtension;
import com.google.android.apps.dashclock.api.ExtensionData;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;

/*
 * This class is the main class that provides the widget
 */
public class LocationWidget extends DashClockExtension {

	/*
	 * @see com.google.android.apps.dashclock.api.DashClockExtension#onCreate()
	 */
	public void onCreate() {

		super.onCreate();
		Log.d("LocationWidget", "Created");
		BugSenseHandler.initAndStartSession(this, getString(R.string.bugsense));

	}

	/*
	 * @see
	 * com.google.android.apps.dashclock.api.DashClockExtension#onUpdateData
	 * (int)
	 */
	@Override
	protected void onUpdateData(int intReason) {

		Log.d("LocationWidget", "Getting the location of the device");
		final ExtensionData edtInformation = new ExtensionData();
		setUpdateWhenScreenOn(true);

		try {

			Log.d("LocationWidget", "Fetching the most recent and accurate fix");
			LocationManager mgrLocation = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

			List<String> lstProviders = mgrLocation.getProviders(true);
			if (lstProviders != null) {

				Location locNewest = null;
				for (String strProvider : lstProviders) {

					Location locLocation = mgrLocation.getLastKnownLocation(strProvider);
					if (locLocation != null) {

						if (locNewest == null) {

							locNewest = locLocation;

						} else {

							if (locLocation.getTime() > locNewest.getTime()) {

								locNewest = locLocation;

							}

						}

						//mgrLocation.requestLocationUpdates(provider, 0, 0, this);

					}

				}

				if (locNewest != null ) {

					final Float fltAccuracy = locNewest.getAccuracy();

					Log.d("LocationWidget", "Making the rrequest to reverse geocode the coordinates");
					new AsyncHttpClient()
					.get("http://maps.googleapis.com/maps/api/geocode/json?latlng=" + locNewest.getLatitude() + "," + locNewest.getLongitude() + "&sensor=false",
							new AsyncHttpResponseHandler() {

						@Override
						public void onSuccess(String strResponse) {

							try {
								
								BugSenseHandler.addCrashExtraData("Response", strResponse);

								Log.v("LocationWidget", "Server reponded with: " + strResponse);
								if (!strResponse.trim().isEmpty()) {

									JSONObject jsoResponse = new JSONObject(strResponse);
									if (jsoResponse.getString("status").equalsIgnoreCase("OK")) {

										BugSenseHandler.sendEvent("Successful Request");
										edtInformation.expandedBody("");
										edtInformation.expandedTitle(getString(R.string.location_info, fltAccuracy.intValue()));

										JSONArray jsoResults = jsoResponse.getJSONArray("results");
										Integer intMinimum = Integer.MAX_VALUE;

										for (Integer intResult = 0; intResult < jsoResults.length(); intResult++) {

											JSONObject jsoBounds = jsoResults.getJSONObject(intResult).getJSONObject("geometry").getJSONObject("viewport");

											float[] fltDistances = new float[1];

											Location.distanceBetween(
													Double.parseDouble(jsoBounds.getJSONObject("northeast").getString("lat")),
													Double.parseDouble(jsoBounds.getJSONObject("northeast").getString("lng")),
													Double.parseDouble(jsoBounds.getJSONObject("southwest").getString("lat")),
													Double.parseDouble(jsoBounds.getJSONObject("southwest").getString("lng")),
													fltDistances);

											if (((int) fltDistances[0] - fltAccuracy) < intMinimum) {

												intMinimum = (int) (fltDistances[0] - fltAccuracy);
												edtInformation.expandedBody(jsoResults.getJSONObject(intResult).getString("formatted_address"));
												edtInformation.clickIntent(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0")));

											}

										}

										edtInformation.visible(true);
										publishUpdate(edtInformation);

									} else if (jsoResponse.getString("status").equalsIgnoreCase("ZERO_RESULTS")) {
										BugSenseHandler.sendEvent("Zero Results");
										Log.w("LocationWidget", "Google Reverse Geocoding API returned no results");
										edtInformation.visible(false);
										Toast.makeText(getApplicationContext(), R.string.zero_results, Toast.LENGTH_SHORT).show();
									} else if (jsoResponse.getString("status").equalsIgnoreCase("OVER_QUERY_LIMIT")) {
										BugSenseHandler.sendEvent("Over Limit");
										Log.w("LocationWidget", "Google Reverse Geocoding API has hit the query limit");
										edtInformation.visible(false);
										Toast.makeText(getApplicationContext(), R.string.over_limit, Toast.LENGTH_SHORT).show();
									} else if (jsoResponse.getString("status").equalsIgnoreCase("REQUEST_DENIED")) {
										BugSenseHandler.sendEvent("Request Denied");
										Log.e("LocationWidget", "Google Reverse Geocoding API denied the request");
										edtInformation.visible(false);
										throw new Exception("Google Reverse Geocoding API said invalid request");
									} else if (jsoResponse.getString("status").equalsIgnoreCase("INVALID_REQUEST")) {
										BugSenseHandler.sendEvent("Invalid Request");
										Log.e("LocationWidget", "Google Reverse Geocoding API said invalid request");
										edtInformation.visible(false);
										throw new Exception("Google Reverse Geocoding API said invalid request");
									} else {
										BugSenseHandler.sendEvent("Unknown Response");
										Log.e("LocationWidget", "Google Reverse Geocoding API encountred an error");
										edtInformation.visible(false);
										throw new Exception("Google Reverse Geocoding API said invalid request");
									}

								}

							} catch (Exception e) {
								edtInformation.visible(false);
								Log.e("LocationWidget", "Encountered an error", e);
								BugSenseHandler.sendException(e);
							} finally {
								BugSenseHandler.clearCrashExtraData();
							}

						}

					});

				}

			}

			if (new Random().nextInt(5) == 0) {

				PackageManager mgrPackages = getApplicationContext().getPackageManager();

				try {

					mgrPackages.getPackageInfo("com.mridang.donate", PackageManager.GET_META_DATA);

				} catch (NameNotFoundException e) {

					Integer intExtensions = 0;
				    Intent ittFilter = new Intent("com.google.android.apps.dashclock.Extension");
				    String strPackage;

				    for (ResolveInfo info : mgrPackages.queryIntentServices(ittFilter, 0)) {

				    	strPackage = info.serviceInfo.applicationInfo.packageName;
						intExtensions = intExtensions + (strPackage.startsWith("com.mridang.") ? 1 : 0); 

					}

					if (intExtensions > 1) {

						edtInformation.visible(true);
						edtInformation.clickIntent(new Intent(Intent.ACTION_VIEW).setData(Uri.parse("market://details?id=com.mridang.donate")));
						edtInformation.expandedTitle("Please consider a one time purchase to unlock.");
						edtInformation.expandedBody("Thank you for using " + intExtensions + " extensions of mine. Click this to make a one-time purchase or use just one extension to make this disappear.");
						setUpdateWhenScreenOn(true);

					}

				}

			} else {
				setUpdateWhenScreenOn(true);
			}

		} catch (Exception e) {
			edtInformation.visible(false);
			Log.e("LocationWidget", "Encountered an error", e);
			BugSenseHandler.sendException(e);
		}

		edtInformation.icon(R.drawable.ic_dashclock);
		publishUpdate(edtInformation);
		Log.d("LocationWidget", "Done");

	}

	/*
	 * @see com.google.android.apps.dashclock.api.DashClockExtension#onDestroy()
	 */
	public void onDestroy() {

		super.onDestroy();
		Log.d("LocationWidget", "Destroyed");
		BugSenseHandler.closeSession(this);

	}

}