package com.mridang.location;

import java.util.List;

import org.acra.ACRA;
import org.json.JSONArray;
import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.google.android.apps.dashclock.api.ExtensionData;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;

/*
 * This class is the main class that provides the widget
 */
public class LocationWidget extends ImprovedExtension {

	/*
	 * (non-Javadoc)
	 * @see com.mridang.location.ImprovedExtension#getIntents()
	 */
	@Override
	protected IntentFilter getIntents() {
		return null;
	}

	/*
	 * (non-Javadoc)
	 * @see com.mridang.location.ImprovedExtension#getTag()
	 */
	@Override
	protected String getTag() {
		return getClass().getSimpleName();
	}

	/*
	 * (non-Javadoc)
	 * @see com.mridang.location.ImprovedExtension#getUris()
	 */
	@Override
	protected String[] getUris() {
		return null;
	}

	/*
	 * @see
	 * com.google.android.apps.dashclock.api.DashClockExtension#onUpdateData
	 * (int)
	 */
	@Override
	protected void onUpdateData(int intReason) {

		Log.d(getTag(), "Getting the location of the device");
		final ExtensionData edtInformation = new ExtensionData();
		setUpdateWhenScreenOn(true);

		try {

			Log.d(getTag(), "Fetching the most recent and accurate fix");
			LocationManager mgrLocation = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
			final SharedPreferences speSettings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

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

						// mgrLocation.requestLocationUpdates(provider, 0, 0,
						// this);

					}

				}

				if (locNewest != null) {

					final Float fltAccuracy = locNewest.getAccuracy();

					Log.d(getTag(), "Making the rrequest to reverse geocode the coordinates");
					new AsyncHttpClient().get(
							"http://maps.googleapis.com/maps/api/geocode/json?latlng=" + locNewest.getLatitude() + ","
									+ locNewest.getLongitude() + "&sensor=false", new AsyncHttpResponseHandler() {

								@Override
								public void onSuccess(String strResponse) {

									try {

										if (!strResponse.trim().isEmpty()) {

											JSONObject jsoResponse = new JSONObject(strResponse);
											if (jsoResponse.getString("status").equalsIgnoreCase("OK")) {

												edtInformation.expandedBody("");
												if (speSettings.getBoolean("usefeet", false)) {
													edtInformation.expandedTitle(getString(R.string.location_feet,
															(int) (fltAccuracy * 3.28F)));
												} else {
													edtInformation.expandedTitle(getString(R.string.location_mtrs,
															fltAccuracy.intValue()));
												}

												JSONArray jsoResults = jsoResponse.getJSONArray("results");
												Integer intMinimum = Integer.MAX_VALUE;

												for (Integer intResult = 0; intResult < jsoResults.length(); intResult++) {

													JSONObject jsoBounds = jsoResults.getJSONObject(intResult)
															.getJSONObject("geometry").getJSONObject("viewport");

													float[] fltDistances = new float[1];

													Location.distanceBetween(Double.parseDouble(jsoBounds
															.getJSONObject("northeast").getString("lat")), Double
															.parseDouble(jsoBounds.getJSONObject("northeast")
																	.getString("lng")), Double.parseDouble(jsoBounds
															.getJSONObject("southwest").getString("lat")), Double
															.parseDouble(jsoBounds.getJSONObject("southwest")
																	.getString("lng")), fltDistances);

													if (((int) fltDistances[0] - fltAccuracy) < intMinimum) {

														intMinimum = (int) (fltDistances[0] - fltAccuracy);
														edtInformation.expandedBody(jsoResults.getJSONObject(intResult)
																.getString("formatted_address"));
														edtInformation.clickIntent(new Intent(Intent.ACTION_VIEW, Uri
																.parse("geo:0,0")));

													}

												}

												edtInformation.visible(true);
												doUpdate(edtInformation);

											} else if (jsoResponse.getString("status").equalsIgnoreCase("ZERO_RESULTS")) {
												Log.w(getTag(),
														"Google Reverse Geocoding API returned no results");
												edtInformation.visible(false);
												Toast.makeText(getApplicationContext(), R.string.zero_results,
														Toast.LENGTH_SHORT).show();
											} else if (jsoResponse.getString("status").equalsIgnoreCase(
													"OVER_QUERY_LIMIT")) {
												Log.w(getTag(),
														"Google Reverse Geocoding API has hit the query limit");
												edtInformation.visible(false);
												Toast.makeText(getApplicationContext(), R.string.over_limit,
														Toast.LENGTH_SHORT).show();
											} else if (jsoResponse.getString("status").equalsIgnoreCase(
													"REQUEST_DENIED")) {
												Log.e(getTag(),
														"Google Reverse Geocoding API denied the request");
												edtInformation.visible(false);
												throw new Exception("Google Reverse Geocoding API said invalid request");
											} else if (jsoResponse.getString("status").equalsIgnoreCase(
													"INVALID_REQUEST")) {
												Log.e(getTag(),
														"Google Reverse Geocoding API said invalid request");
												edtInformation.visible(false);
												throw new Exception("Google Reverse Geocoding API said invalid request");
											} else {
												Log.e(getTag(),
														"Google Reverse Geocoding API encountred an error");
												edtInformation.visible(false);
												throw new Exception("Google Reverse Geocoding API said invalid request");
											}

										}

									} catch (Exception e) {
										edtInformation.visible(false);
										Log.e(getTag(), "Encountered an error", e);
										ACRA.getErrorReporter().handleSilentException(e);
									}

								}

							});

				}

			}

		} catch (Exception e) {
			edtInformation.visible(false);
			Log.e(getTag(), "Encountered an error", e);
			ACRA.getErrorReporter().handleSilentException(e);
		}

		edtInformation.icon(R.drawable.ic_dashclock);
		doUpdate(edtInformation);

	}

	/*
	 * (non-Javadoc)
	 * @see com.mridang.location.ImprovedExtension#onReceiveIntent(android.content.Context, android.content.Intent)
	 */
	@Override
	protected void onReceiveIntent(Context ctxContext, Intent ittIntent) {
		onUpdateData(UPDATE_REASON_MANUAL);
	}

}