package com.lyokone.location;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.location.OnNmeaMessageListener;
import android.os.Build;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.BDNotifyListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.location.Poi;

import io.flutter.plugin.common.EventChannel.EventSink;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;

class FlutterLocation
        implements PluginRegistry.RequestPermissionsResultListener, PluginRegistry.ActivityResultListener {
    private static final String TAG = "FlutterLocation";

    private final Context applicationContext;

    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 34;
    private static final int REQUEST_CHECK_SETTINGS = 0x1;

    private static final int GPS_ENABLE_REQUEST = 0x1001;

    @Nullable
    public Activity activity;

    private LocationClient mLocationClient = null;

    public EventSink events;

    private boolean isPurporseLoc = false;
    private boolean isInChina = false;
    private boolean isNotify = false;
    private BDNotifyListener mNotifyListener;

    // Store result until a permission check is resolved
    public Result result;

    // Store result until a location is getting resolved
    public Result getLocationResult;

    private int locationPermissionState;

    FlutterLocation(Context applicationContext, @Nullable Activity activity) {
        this.applicationContext = applicationContext;
        this.activity = activity;
    }

    FlutterLocation(PluginRegistry.Registrar registrar) {
        this(registrar.context(), registrar.activity());
        registrar.addRequestPermissionsResultListener(this);
    }

    void setActivity(@Nullable Activity activity) {
        this.activity = activity;
    }

    /**
     * 停止定位
     */
    public void stopLocation() {
        if (null != mLocationClient) {
            mLocationClient.stop();
            mLocationClient = null;
        }
    }

    /**
     * 开始定位
     */
    public void startLocation() {
        if(null != mLocationClient && !mLocationClient.isStarted()) {
            mLocationClient.start();
        }
    }

    /**
     * 准备定位
     * @param arguments
     */
    public void updateOption(Map arguments) {
        if (null == mLocationClient) {
            mLocationClient = new LocationClient(applicationContext);
        }
        // 判断是否启用位置提醒功能
        if (arguments.containsKey("isNotify")) {
            isNotify = true;
            if (null == mNotifyListener) {
                mNotifyListener = new MyNotifyLister();
            }
            mLocationClient.registerNotify(mNotifyListener);
            double lat = 0;
            double lon = 0;
            float radius = 0;

            if (arguments.containsKey("latitude")) {
                lat = (double)arguments.get("latitude");
            }

            if (arguments.containsKey("longitude")) {
                lon = (double)arguments.get("longitude");
            }

            if (arguments.containsKey("radius")) {
                double radius1 = (double)arguments.get("radius");
                radius = Float.parseFloat(String.valueOf(radius1));
            }

            String coorType = mLocationClient.getLocOption().getCoorType();
            mNotifyListener.SetNotifyLocation(lat, lon, radius, coorType);
            return;
        } else {
            isNotify = false;
        }

        mLocationClient.registerLocationListener(new CurrentLocationListener());

        // 判断是否启用国内外位置判断功能
        if (arguments.containsKey("isInChina")) {
            isInChina = true;
            return;
        } else {
            isInChina =false;
        }


        LocationClientOption option = new LocationClientOption();
        parseOptions(option, arguments);
        option.setProdName("flutter");
        mLocationClient.setLocOption(option);
    }

    /**
     * 解析定位参数
     * @param option
     * @param arguments
     */
    private void parseOptions(LocationClientOption option,Map arguments) {
        if (arguments != null) {

            // 可选，设置是否返回逆地理地址信息。默认是true
            if (arguments.containsKey("isNeedAddres")) {
                if (((boolean)arguments.get("isNeedAddres"))) {
                    option.setIsNeedAddress(true);
                } else {
                    option.setIsNeedAddress(false);
                }
            }

            // 可选，设置定位模式，可选的模式有高精度、仅设备、仅网络。默认为高精度模式
            if (arguments.containsKey("locationMode")) {
                if (((int)arguments.get("locationMode")) == 1) {
                    option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy); // 高精度模式
                } else if (((int)arguments.get("locationMode")) == 2) {
                    option.setLocationMode(LocationClientOption.LocationMode.Device_Sensors); // 仅设备模式
                } else if (((int)arguments.get("locationMode")) == 3) {
                    option.setLocationMode(LocationClientOption.LocationMode.Battery_Saving); // 仅网络模式
                }
            }

            // 可选，设置场景定位参数，包括签到场景、运动场景、出行场景
            if ((arguments.containsKey("LocationPurpose"))) {
                isPurporseLoc = true;
                if  (((int)arguments.get("LocationPurpose")) == 1) {
                    option.setLocationPurpose(LocationClientOption.BDLocationPurpose.SignIn); // 签到场景
                } else if (((int)arguments.get("LocationPurpose")) == 2) {
                    option.setLocationPurpose(LocationClientOption.BDLocationPurpose.Transport); // 运动场景
                } else if (((int)arguments.get("LocationPurpose")) == 3) {
                    option.setLocationPurpose(LocationClientOption.BDLocationPurpose.Sport); // 出行场景
                }
            } else {
                isPurporseLoc = false;
            }

            // 可选，设置需要返回海拔高度信息
            if (arguments.containsKey("isNeedAltitude")) {
                if (((boolean)arguments.get("isNeedAltitude"))) {
                    option.setIsNeedAddress(true);
                } else {
                    option.setIsNeedAltitude(false);
                }
            }

            // 可选，设置是否使用gps，默认false
            if (arguments.containsKey("openGps")) {
                if(((boolean)arguments.get("openGps"))) {
                    option.setOpenGps(true);
                } else {
                    option.setOpenGps(false);
                }
            }

            // 可选，设置是否允许返回逆地理地址信息，默认是true
            if (arguments.containsKey("isNeedLocationDescribe")) {
                if(((boolean)arguments.get("isNeedLocationDescribe"))) {
                    option.setIsNeedLocationDescribe(true);
                } else {
                    option.setIsNeedLocationDescribe(false);
                }
            }

            // 可选，设置发起定位请求的间隔，int类型，单位ms
            // 如果设置为0，则代表单次定位，即仅定位一次，默认为0
            // 如果设置非0，需设置1000ms以上才有效
            if (arguments.containsKey("scanspan")) {
                option.setScanSpan((int)arguments.get("scanspan"));
            }
            // 可选，设置返回经纬度坐标类型，默认GCJ02
            // GCJ02：国测局坐标；
            // BD09ll：百度经纬度坐标；
            // BD09：百度墨卡托坐标；
            // 海外地区定位，无需设置坐标类型，统一返回WGS84类型坐标
            if (arguments.containsKey("coorType")) {
                option.setCoorType((String)arguments.get("coorType"));
            }

            // 设置是否需要返回附近的poi列表
            if (arguments.containsKey("isNeedLocationPoiList")) {
                if (((boolean)arguments.get("isNeedLocationPoiList"))) {
                    option.setIsNeedLocationPoiList(true);
                } else {
                    option.setIsNeedLocationPoiList(false);
                }
            }
            // 设置是否需要最新版本rgc数据
            if (arguments.containsKey("isNeedNewVersionRgc")) {
                if (((boolean)arguments.get("isNeedNewVersionRgc"))) {
                    option.setIsNeedLocationPoiList(true);
                } else {
                    option.setIsNeedLocationPoiList(false);
                }
            }
        }
    }

    /**
     * 格式化时间
     *
     * @param time
     * @param strPattern
     * @return
     */
    private String formatUTC(long time, String strPattern) {
        if (TextUtils.isEmpty(strPattern)) {
            strPattern = "yyyy-MM-dd HH:mm:ss";
        }
        SimpleDateFormat sdf = null;
        try {
            sdf = new SimpleDateFormat(strPattern, Locale.CHINA);
            sdf.applyPattern(strPattern);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return sdf == null ? "NULL" : sdf.format(time);
    }

    void sendResult(Map<String, Object> result){
        if (getLocationResult != null) {
            getLocationResult.success(result);
            getLocationResult = null;
        }
        if (events != null) {
            events.success(result);
        }
    }



    class CurrentLocationListener extends BDAbstractLocationListener {

        @Override
        public void onReceiveLocation(BDLocation bdLocation) {

            Map<String, Object> result = new LinkedHashMap<>();

            // 场景定位获取结果
            if (isPurporseLoc || true) {
                result.put("latitude", bdLocation.getLatitude()); // 纬度
                result.put("longitude", bdLocation.getLongitude()); // 经度
                sendResult(result);
                return;
            }

            result.put("callbackTime", formatUTC(System.currentTimeMillis(), "yyyy-MM-dd HH:mm:ss"));
            if (null != bdLocation) {
                if (bdLocation.getLocType() == BDLocation.TypeGpsLocation
                        || bdLocation.getLocType() == BDLocation.TypeNetWorkLocation
                        || bdLocation.getLocType() == BDLocation.TypeOffLineLocation) {
                    result.put("locType", bdLocation.getLocType()); // 定位结果类型
                    result.put("locTime", bdLocation.getTime()); // 定位成功时间
                    result.put("latitude", bdLocation.getLatitude()); // 纬度
                    result.put("longitude", bdLocation.getLongitude()); // 经度
                    if (bdLocation.hasAltitude()) {
                        result.put("altitude", bdLocation.getAltitude()); // 高度
                    }
                    result.put("radius", Double.parseDouble(String.valueOf(bdLocation.getRadius()))); // 定位精度
                    result.put("country", bdLocation.getCountry()); // 国家
                    result.put("province", bdLocation.getProvince()); // 省份
                    result.put("city", bdLocation.getCity()); // 城市
                    result.put("district", bdLocation.getDistrict()); // 区域
                    result.put("town", bdLocation.getTown()); // 城镇
                    result.put("street", bdLocation.getStreet()); // 街道
                    result.put("address", bdLocation.getAddrStr()); // 地址
                    result.put("locationDetail", bdLocation.getLocationDescribe()); // 位置语义化描述
                    if (null != bdLocation.getPoiList() && !bdLocation.getPoiList().isEmpty()) {

                        List<Poi> pois = bdLocation.getPoiList();
                        StringBuilder stringBuilder = new StringBuilder();

                        if (pois.size() == 1) {
                            stringBuilder.append(pois.get(0).getName()).append(",").append(pois.get(0).getTags())
                                    .append(pois.get(0).getAddr());
                        } else {
                            for (int i = 0; i < pois.size() - 1; i++) {
                                stringBuilder.append(pois.get(i).getName()).append(",").append(pois.get(i).getTags())
                                        .append(pois.get(i).getAddr()).append("|");
                            }
                            stringBuilder.append(pois.get(pois.size()-1).getName()).append(",").append(pois.get(pois.size()-1).getTags())
                                    .append(pois.get(pois.size()-1).getAddr());

                        }

                        result.put("poiList",stringBuilder.toString()); // 周边poi信息
//
                    }
                    if (bdLocation.getFloor() != null) {
                        // 当前支持高精度室内定位
                        String buildingID = bdLocation.getBuildingID();// 百度内部建筑物ID
                        String buildingName = bdLocation.getBuildingName();// 百度内部建筑物缩写
                        String floor = bdLocation.getFloor();// 室内定位的楼层信息，如 f1,f2,b1,b2
                        StringBuilder stringBuilder = new StringBuilder();
                        stringBuilder.append(buildingID).append("-").append(buildingName).append("-").append(floor);
                        result.put("indoor", stringBuilder.toString()); // 室内定位结果信息
                        mLocationClient.startIndoorMode();// 开启室内定位模式（重复调用也没问题），开启后，定位SDK会融合各种定位信息（GPS,WI-FI，蓝牙，传感器等）连续平滑的输出定位结果；
                    } else {
                        mLocationClient.stopIndoorMode(); // 处于室外则关闭室内定位模式
                    }
                } else {
                    result.put("errorCode", bdLocation.getLocType()); // 定位结果错误码
                    result.put("errorInfo", bdLocation.getLocTypeDescription()); // 定位失败描述信息
                }
            } else {
                result.put("errorCode", -1);
                result.put("errorInfo", "location is null");
            }
            sendResult(result);
            // android端实时检测位置变化，将位置结果发送到flutter端
        }
    }

    public class MyNotifyLister extends BDNotifyListener {
        // 已到达设置监听位置附近
        public void onNotify(BDLocation mlocation, float distance){
            if (null == events) {
                return;
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("nearby", "已到达设置监听位置附近"); // 1为已经到达 0为未到达
            events.success(result);
        }
    }

    @Override
    public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        return onRequestPermissionsResultHandler(requestCode, permissions, grantResults);
    }

    public boolean onRequestPermissionsResultHandler(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE && permissions.length == 1
                && permissions[0].equals(Manifest.permission.ACCESS_FINE_LOCATION)) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Checks if this permission was automatically triggered by a location request
                if (getLocationResult != null || events != null) {
                    startRequestingLocation();
                }
                if (result != null) {
                    result.success(1);
                    result = null;
                }
            } else {
                if (!shouldShowRequestPermissionRationale()) {
                    sendError("PERMISSION_DENIED_NEVER_ASK",
                            "Location permission denied forever - please open app settings", null);
                    if (result != null) {
                        result.success(2);
                        result = null;
                    }
                } else {
                    sendError("PERMISSION_DENIED", "Location permission denied", null);
                    if (result != null) {
                        result.success(0);
                        result = null;
                    }
                }
            }
            return true;
        }
        return false;

    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (result == null) {
            return false;
        }
        switch (requestCode) {
            case GPS_ENABLE_REQUEST:
                if (resultCode == Activity.RESULT_OK) {
                    result.success(1);
                } else {
                    result.success(0);
                }
                result = null;
                return true;
            case REQUEST_CHECK_SETTINGS:
                if (resultCode == Activity.RESULT_OK) {
                    startRequestingLocation();
                    return true;
                }

                result.error("SERVICE_STATUS_DISABLED", "Failed to get location. Location services disabled", null);
                result = null;
                return true;
            default:
                return false;
        }
    }



    private void sendError(String errorCode, String errorMessage, Object errorDetails) {
        if (getLocationResult != null) {
            getLocationResult.error(errorCode, errorMessage, errorDetails);
            getLocationResult = null;
        }
        if (events != null) {
            events.error(errorCode, errorMessage, errorDetails);
            events = null;
        }
    }


    /**
     * Return the current state of the permissions needed.
     */
    public boolean checkPermissions() {
        this.locationPermissionState = ActivityCompat.checkSelfPermission(activity,
                Manifest.permission.ACCESS_FINE_LOCATION);
        return this.locationPermissionState == PackageManager.PERMISSION_GRANTED;
    }

    public void requestPermissions() {
        if (checkPermissions()) {
            result.success(1);
            return;
        }
        ActivityCompat.requestPermissions(activity, new String[] { Manifest.permission.ACCESS_FINE_LOCATION },
                REQUEST_PERMISSIONS_REQUEST_CODE);
    }

    public boolean shouldShowRequestPermissionRationale() {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION);
    }

    /** Checks whether location services is enabled. */
    public boolean checkServiceEnabled() {
        return true;
    }

    public void requestService(final Result result) {
        try {
            if (this.checkServiceEnabled()) {
                result.success(1);
                return;
            }
        } catch (Exception e) {
            result.error("SERVICE_STATUS_ERROR", "Location service status couldn't be determined", null);
            return;
        }

        this.result = result;
    }

    public void startRequestingLocation() {
        startLocation();
        if(mLocationClient != null){
            mLocationClient.requestLocation();
        }
        
    }

}
