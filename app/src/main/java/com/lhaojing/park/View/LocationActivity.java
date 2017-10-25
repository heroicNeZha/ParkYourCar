package com.lhaojing.park.View;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Toast;

import com.amap.api.maps.AMap;
import com.amap.api.maps.AMapOptions;
import com.amap.api.maps.AMapUtils;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.UiSettings;
import com.amap.api.maps.model.CameraPosition;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.MyLocationStyle;
import com.amap.api.maps.model.Poi;
import com.amap.api.navi.AmapNaviPage;
import com.amap.api.navi.AmapNaviParams;
import com.amap.api.navi.AmapNaviType;
import com.amap.api.navi.INaviInfoCallback;
import com.amap.api.navi.model.AMapNaviLocation;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.core.PoiItem;
import com.amap.api.services.poisearch.PoiResult;
import com.amap.api.services.poisearch.PoiSearch;
import com.lhaojing.park.R;
import com.lhaojing.park.Utils.SpeechUtils;

import java.util.ArrayList;
import java.util.List;

public class LocationActivity extends AppCompatActivity implements
        PoiSearch.OnPoiSearchListener , INaviInfoCallback{

    private static final String TAG = "LocationActivity";
    //地图
    private MapView mMapView = null;
    private MyLocationStyle myLocationStyle;
    private AMap aMap;
    private UiSettings mUiSettings;
    //停车场检索
    private PoiSearch.Query query;
    private PoiSearch poiSearch;
    //驾车路线
    private LatLng startLatlng = null;
    private Poi targetPoi = null;

    private boolean isCameraSet;
    private boolean isFirstIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        //自己设置导航
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Poi start = new Poi("我的位置", startLatlng, "");
                if(targetPoi!=null)
                AmapNaviPage.getInstance().showRouteActivity(getApplicationContext(), new AmapNaviParams(start, null , targetPoi, AmapNaviType.DRIVER), LocationActivity.this);
            }
        });

        //地图
        mMapView = (MapView) findViewById(R.id.map);
        mMapView.onCreate(savedInstanceState);
        init();

        //权限
        List<String> permissionList = new ArrayList<>();
        if(ContextCompat.checkSelfPermission(LocationActivity.this, Manifest.
                permission.ACCESS_FINE_LOCATION)!= PackageManager.PERMISSION_GRANTED){
            permissionList.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if(ContextCompat.checkSelfPermission(LocationActivity.this, Manifest.
                permission.READ_PHONE_STATE)!= PackageManager.PERMISSION_GRANTED){
            permissionList.add(Manifest.permission.READ_PHONE_STATE);
        }
        if(ContextCompat.checkSelfPermission(LocationActivity.this, Manifest.
                permission.WRITE_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED){
            permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if(!permissionList.isEmpty()){
            String[] permissions = permissionList.toArray(new String[permissionList.size()]);
            ActivityCompat.requestPermissions(LocationActivity.this,permissions,1);
        }
    }

    private void init(){
        isCameraSet = false;
        isFirstIndex = false;

        if (aMap == null) {
            aMap = mMapView.getMap();
        }
        //定位
        myLocationStyle = new MyLocationStyle()
                .interval(5000)
                .myLocationType(MyLocationStyle.LOCATION_TYPE_MAP_ROTATE_NO_CENTER);
        aMap.setMyLocationStyle(myLocationStyle);
        aMap.setOnMyLocationChangeListener(new myLocationChangeListener());
        aMap.getUiSettings().setMyLocationButtonEnabled(true);
        aMap.setOnMarkerClickListener(markerClickListener);
        aMap.setTrafficEnabled(true);
        aMap.setMyLocationEnabled(true);
        //UI
        mUiSettings = aMap.getUiSettings();
        mUiSettings.setScaleControlsEnabled(true);
        mUiSettings.setZoomControlsEnabled(false);
        mUiSettings.setLogoPosition(AMapOptions.LOGO_POSITION_BOTTOM_LEFT);
    }

    AMap.OnMarkerClickListener markerClickListener = new AMap.OnMarkerClickListener() {

        @Override
        public boolean onMarkerClick(Marker marker) {
            targetPoi = new Poi(marker.getTitle(),marker.getPosition(),"");
            return false;
        }
    };

    @Override
    public void onPoiSearched(PoiResult poiResult, int i) {
//        FragmentManager fragmentManager =getFragmentManager();
//        FragmentTransaction transaction = fragmentManager.beginTransaction();
//        ParkItemFragment parkItemFragment = new ParkItemFragment();
//        transaction.add(R.id.parkFrag, parkItemFragment);
//        transaction.commit();

        int index = 0;
        for (PoiItem pi:poiResult.getPois()) {
            index ++;
            LatLng targetLatLng = new LatLng(pi.getLatLonPoint().getLatitude(),pi.getLatLonPoint().getLongitude());
            MarkerOptions mo =  new MarkerOptions()
                    .position(targetLatLng)
                    .title(pi.getTitle())
                    .snippet( String.format("据您%.1f米\n%s",AMapUtils.calculateLineDistance(startLatlng,targetLatLng), pi.getSnippet()));
            if(!isFirstIndex&&index==1){
                Marker targetMk = aMap.addMarker(mo);
                targetPoi = new Poi(mo.getTitle(),mo.getPosition(),"");
                targetMk.showInfoWindow();
                isFirstIndex = true;
            }else {
                aMap.addMarker(mo);
            }
        }
    }

    @Override
    public void onPoiItemSearched(PoiItem poiItem, int i) {
    }

    //POI检索停车场
    private void findPark(){
        String keyword = "停车场";
        String citycode = "沈阳";
        int currentPage = 1;
        query = new PoiSearch.Query(keyword,"",citycode);
        query.setPageSize(20);
        query.setPageNum(currentPage);
        poiSearch = new PoiSearch(this, query);
        poiSearch.setOnPoiSearchListener(this);
        poiSearch.setBound(new PoiSearch.SearchBound(new LatLonPoint(startLatlng.latitude,startLatlng.longitude), 2000));
        poiSearch.searchPOIAsyn();
    }

    //权限
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults){
        switch (requestCode){
            case 1:
                if (grantResults.length>0){
                    for (int result:grantResults){
                        if(result != PackageManager.PERMISSION_GRANTED){
                            Toast.makeText(this,"请同意啦!",Toast.LENGTH_SHORT).show();
                            finish();
                            return;
                        }
                    }
                }else{
                    Toast.makeText(this,"错误!",Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            default:
        }
    }
    //导航
    @Override
    public void onInitNaviFailure() {

    }

    @Override
    public void onGetNavigationText(String s) {
        SpeechUtils.getInstance(this).speakText(s);
    }

    @Override
    public void onLocationChange(AMapNaviLocation aMapNaviLocation) {

    }

    @Override
    public void onArriveDestination(boolean b) {

    }

    @Override
    public void onStartNavi(int i) {

    }

    @Override
    public void onCalculateRouteSuccess(int[] ints) {

    }

    @Override
    public void onCalculateRouteFailure(int i) {

    }

    @Override
    public void onStopSpeaking() {

    }

    //定位
    private class myLocationChangeListener implements AMap.OnMyLocationChangeListener{

        @Override
        public void onMyLocationChange(Location location) {
            startLatlng = new LatLng(location.getLatitude(),location.getLongitude());
            if (!isCameraSet) {
                aMap.animateCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition(
                        new LatLng(location.getLatitude(), location.getLongitude()), 15f, 15, 0)));
                isCameraSet = true;
            }
            findPark();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mMapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mMapView.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mMapView.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMapView.onDestroy();
    }
}
