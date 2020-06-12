package com.mapbox.mapboxgl;

import android.app.Activity;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ProgressBar;


import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.offline.OfflineManager;
import com.mapbox.mapboxsdk.offline.OfflineRegion;
import com.mapbox.mapboxsdk.offline.OfflineRegionError;
import com.mapbox.mapboxsdk.offline.OfflineRegionStatus;
import com.mapbox.mapboxsdk.offline.OfflineTilePyramidRegionDefinition;
import com.mapbox.mapboxsdk.Mapbox;

import org.json.JSONObject;
import java.util.ArrayList;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;



public class MapboxOfflineManager implements MethodChannel.MethodCallHandler {
    public static final String JSON_CHARSET = "UTF-8";
    public static final String JSON_FIELD_REGION_NAME = "FIELD_REGION_NAME";
    public static final String TAG = "MapboxOfflineManager";
    private final String CHANNEL_1_ID = "ch1";
    Context context;
    MapboxMap map;
    // private OfflineManager offlineManager;
    private OfflineRegion offlineRegion;
    // private boolean isEndNotified;
    private NotificationManager notificationManager;
    private MethodChannel methodChannel;
    private final PluginRegistry.Registrar registrar;


    MapboxOfflineManager(PluginRegistry.Registrar registrar){
        this.registrar = registrar;
        context = registrar.context();
        Mapbox.getInstance(context, "pk.eyJ1Ijoibm92YWRldiIsImEiOiJjamw2ZTViZnEybmR6M3Bud2xuaHkwd2pwIn0.ZaIS6cuBNI5yaVz2U6caKg");

        methodChannel = new MethodChannel(registrar.messenger(), "plugins.flutter.io/offline_map");
        methodChannel.setMethodCallHandler(this);


        
        // offlineManager = OfflineManager.getInstance(context);
        // isEndNotified = false;
 
        // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        //     NotificationChannel channel1 = new NotificationChannel(
        //             CHANNEL_1_ID,
        //             "Channel 1",
        //             NotificationManager.IMPORTANCE_HIGH
        //     );

        //     notificationManager = context.getSystemService(NotificationManager.class);
        //     notificationManager.createNotificationChannel(channel1);

        // }



    }

    public void downloadRegion(final String regionName, LatLngBounds bounds, double minZoom, double maxZoom, String styleUrl) {

        

        // LatLngBounds bounds = map.getProjection().getVisibleRegion().latLngBounds;
        // double minZoom = map.getCameraPosition().zoom;
        // double maxZoom = map.getMaxZoomLevel();
        // String styleUrl = "mapbox://styles/mapbox/streets-v11";
        // LatLngBounds bounds = LatLngBounds.from(47.361094, 7.181070, 47.143252, 6.836828);
        // double minZoom = 11;
        // double maxZoom = 18;


        float pixelRatio = context.getResources().getDisplayMetrics().density;
        OfflineTilePyramidRegionDefinition definition = new OfflineTilePyramidRegionDefinition(
                styleUrl, bounds, minZoom, maxZoom, pixelRatio);

        // Build a JSONObject using the user-defined offline region title,
        // convert it into string, and use it to create a metadata variable.
        // The metadata variable will later be passed to createOfflineRegion()
        byte[] metadata;
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put(JSON_FIELD_REGION_NAME, regionName);
            String json = jsonObject.toString();
            metadata = json.getBytes(JSON_CHARSET);
        } catch (Exception exception) {

            Log.d(TAG,"Failed to encode metadata: "+ exception.getMessage());
            metadata = null;
        }
        // Create the offline region and launch the download
        OfflineManager.getInstance(context).createOfflineRegion(definition, metadata, new OfflineManager.CreateOfflineRegionCallback() {
            @Override
            public void onCreate(OfflineRegion _offlineRegion) {
                Log.d( TAG,"Offline region created: " + regionName);
                offlineRegion = _offlineRegion;
                launchDownload();
            }
            @Override
            public void onError(String error) {
                Log.e( TAG, "Error: "+ error);
            }
        });
    }


    private void launchDownload() {

        offlineRegion.setObserver(new OfflineRegion.OfflineRegionObserver() {
            @Override
            public void onStatusChanged(OfflineRegionStatus status) {
                // Compute a percentage
                double percentage = status.getRequiredResourceCount() >= 0
                        ? (100.0 * status.getCompletedResourceCount() / status.getRequiredResourceCount()) :
                        0.0;

                methodChannel.invokeMethod("downloadProgressUpdate", percentage);

                if (status.isComplete()) {
                    methodChannel.invokeMethod("downloadProgressComplete", new ArrayList<>());
                    return;
                }
            }

            @Override
            public void onError(OfflineRegionError error) {
                Log.d(TAG,"error: "+error.getMessage());
                Log.d(TAG,"error: "+error.getReason());
                methodChannel.invokeMethod("downloadProgressError", new ArrayList<>());

            }

            @Override
            public void mapboxTileCountLimitExceeded(long limit) {
                Log.d(TAG,"tile count limit exceeded: "+limit);
                methodChannel.invokeMethod("downloadProgressLimitExceeded", new ArrayList<>());
            }
        });

        // Change the region state
        offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE);
    }

    public void getDownloadedTiles() {
        // Query the DB asynchronously
        OfflineManager.getInstance(context).listOfflineRegions(new OfflineManager.ListOfflineRegionsCallback(){
            @Override
            public void onList(OfflineRegion[] offlineRegions) {
                // Check result. If no regions have been
                // downloaded yet, notify user and return
                if (offlineRegions == null || offlineRegions.length == 0) {
                    //Toast.makeText(getApplicationContext(), getString(R.string.toast_no_regions_yet), Toast.LENGTH_SHORT).show();
                    methodChannel.invokeMethod("retrieveDownloadedTileNames", new ArrayList<>());
                    return;
                }

                // Add all of the region names to a list
                ArrayList<String> offlineRegionsNames = new ArrayList<>();
                for (OfflineRegion offlineRegion : offlineRegions) {

                    String name = getRegionName(offlineRegion);
                    if (name.length()>0) {
                        offlineRegionsNames.add(name);
                    }
                }
                methodChannel.invokeMethod("retrieveDownloadedTileNames", offlineRegionsNames);
            }

            @Override
            public void onError(String error) {

            }
        });

    }
    private String getRegionName(OfflineRegion offlineRegion) {
        // Get the region name from the offline region metadata
        String regionName;
        try {
            byte[] metadata = offlineRegion.getMetadata();
            String json = new String(metadata, JSON_CHARSET);
            JSONObject jsonObject = new JSONObject(json);
            regionName = jsonObject.getString(JSON_FIELD_REGION_NAME);
        } catch (Exception exception) {
            Log.e(TAG,"Failed to decode metadata: "+ exception.getMessage());
            regionName = "";
        }
        return regionName;
    }

    public void deleteRegion(int regionSelected){
        OfflineManager.getInstance(context).listOfflineRegions(new OfflineManager.ListOfflineRegionsCallback(){
            @Override
            public void onList(OfflineRegion[] offlineRegions) {
                // Check result. If no regions have been
                // downloaded yet, notify user and return
                if (offlineRegions == null || offlineRegions.length == 0) {
                    return;
                }
                offlineRegions[regionSelected].delete(new OfflineRegion.OfflineRegionDeleteCallback() {
                    @Override
                    public void onDelete() {
                        Log.d(TAG,regionSelected+" deleted");
                    }

                    @Override
                    public void onError(String error) {

                    }
                });
            }

            @Override
            public void onError(String error) {

            }
        });

    }

    // public void navigateToRegion(int regionSelected){
    //     offlineManager.listOfflineRegions(new OfflineManager.ListOfflineRegionsCallback(){
    //         @Override
    //         public void onList(OfflineRegion[] offlineRegions) {
    //             // Check result. If no regions have been
    //             // downloaded yet, notify user and return
    //             if (offlineRegions == null || offlineRegions.length == 0) {
    //                 return;
    //             }
    //             // Get the region bounds and zoom
    //             LatLngBounds bounds = (offlineRegions[regionSelected].getDefinition()).getBounds();
    //             double regionZoom = (offlineRegions[regionSelected].getDefinition()).getMinZoom();

    //             // Create new camera position
    //             CameraPosition cameraPosition = new CameraPosition.Builder()
    //                     .target(bounds.getCenter())
    //                     .zoom(regionZoom)
    //                     .build();

    //             // Move camera to new position
    //             // map.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));

    //         }

    //         @Override
    //         public void onError(String error) {

    //         }
    //     });


    // }

    @Override
    public void onMethodCall(MethodCall call, MethodChannel.Result result) {

        // Log.d(TAG,"=========== onMethodCall ===============");

        switch (call.method) {
            case "offline#downloadOnClick":
                // Log.d(TAG,"DOWNLOAD OFFLINE "+call.argument("downloadName"));
                // String regionName = call.argument("downloadName");
                // String regionName = "test";

                // if(regionName.length()==0){
                //     regionName = map.getProjection().getVisibleRegion().latLngBounds.toString();
                // }

                String regionName = call.argument("downloadName");
                LatLngBounds bounds = LatLngBounds.from(call.argument("north"), call.argument("east"), call.argument("south"), call.argument("west")); ;
                double minZoom = call.argument("minZoom");
                double maxZoom = call.argument("maxZoom");
                String styleUrl = call.argument("styleUrl");

                downloadRegion(regionName, bounds, minZoom, maxZoom, styleUrl);
                break;
            case "offline#getDownloadedTiles":
                getDownloadedTiles();
                break;
            case "offline#deleteDownloadedTiles":
                deleteRegion(call.argument("indexToDelete"));
                break;
            // case "offline#navigateToRegion":
            //     navigateToRegion(call.argument("indexToNavigate"));
            //     break;

            default:
                result.notImplemented();
        }
    }
}





