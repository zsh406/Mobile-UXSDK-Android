package com.dji.ux.sample;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.util.Log;

import dji.ux.widget.FPVOverlayWidget;
import dji.ux.widget.FPVWidget;

import dji.common.error.DJIError;
import dji.common.gimbal.Attitude;
import dji.common.gimbal.GimbalMode;
import dji.common.gimbal.Rotation;
import dji.common.mission.hotpoint.HotpointHeading;
import dji.common.mission.hotpoint.HotpointMission;
import dji.common.mission.hotpoint.HotpointStartPoint;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointAction;
import dji.common.mission.waypoint.WaypointActionType;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionFinishedAction;
import dji.common.mission.waypoint.WaypointMissionFlightPathMode;
import dji.common.mission.waypoint.WaypointMissionGotoWaypointMode;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.model.LocationCoordinate2D;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.mission.MissionControl;
import dji.sdk.mission.Triggerable;
import dji.sdk.mission.timeline.TimelineElement;
import dji.sdk.mission.timeline.TimelineEvent;
import dji.sdk.mission.timeline.TimelineMission;
import dji.sdk.mission.timeline.actions.AircraftYawAction;
import dji.sdk.mission.timeline.actions.GimbalAttitudeAction;
import dji.sdk.mission.timeline.actions.GoHomeAction;
import dji.sdk.mission.timeline.actions.GoToAction;
import dji.sdk.mission.timeline.actions.HotpointAction;
import dji.sdk.mission.timeline.actions.RecordVideoAction;
import dji.sdk.mission.timeline.actions.ShootPhotoAction;
import dji.sdk.mission.timeline.actions.TakeOffAction;
import dji.sdk.mission.timeline.triggers.AircraftLandedTrigger;
import dji.sdk.mission.timeline.triggers.BatteryPowerLevelTrigger;
import dji.sdk.mission.timeline.triggers.Trigger;
import dji.sdk.mission.timeline.triggers.TriggerEvent;
import dji.sdk.mission.timeline.triggers.WaypointReachedTrigger;
import dji.sdk.products.Aircraft;


public class CustomizedWidgetsActivity extends Activity implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {
    private FPVWidget fpvWidget;
    private FPVOverlayWidget fpvOverlayWidget;
    private FPVWidget secondaryFpvWidget;


    private EditText mEditText;
    private CheckBox mCheckbox;
    private boolean isOriginalSize = true;

    private MissionControl missionControl;
    private FlightController flightController;
    private TimelineEvent preEvent;
    private TimelineElement preElement;
    private DJIError preError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customized_widgets);
        initView();

    }

    private void initView() {
        fpvWidget = (FPVWidget)findViewById(R.id.fpv_custom_widget);
        fpvOverlayWidget = (FPVOverlayWidget) findViewById(R.id.fpv_overlay_widget);
        secondaryFpvWidget = (FPVWidget)findViewById(R.id.secondary_fpv_widget);
        ((CheckBox) findViewById(R.id.checkboxPrimaryCameraName)).setOnCheckedChangeListener(this);
        ((CheckBox) findViewById(R.id.checkboxSecondaryCameraName)).setOnCheckedChangeListener(this);
        ((CheckBox) findViewById(R.id.checkbox_touch_focus)).setOnCheckedChangeListener(this);
        ((CheckBox) findViewById(R.id.checkbox_touch_metering)).setOnCheckedChangeListener(this);
        ((CheckBox) findViewById(R.id.checkbox_gimbal_control)).setOnCheckedChangeListener(this);

        findViewById(R.id.primary_video_feed).setOnClickListener(this);
        findViewById(R.id.secondary_video_feed).setOnClickListener(this);
        findViewById(R.id.auto_video_feed).setOnClickListener(this);
        findViewById(R.id.change_size).setOnClickListener(this);

        mEditText = (EditText)findViewById(R.id.pano_type);
        mCheckbox = (CheckBox)findViewById(R.id.useGimbal);
    }


    // On click event for button
    public void resizeView() {

        ViewGroup.LayoutParams params = fpvWidget.getLayoutParams();
        if (!isOriginalSize) {
            params.height = 2 * fpvWidget.getHeight();
            params.width = 2 * fpvWidget.getWidth();
        } else {
            params.height = fpvWidget.getHeight()/2;
            params.width = fpvWidget.getWidth()/2;
        }
        isOriginalSize = !isOriginalSize;
        fpvWidget.setLayoutParams(params);

    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.primary_video_feed:
                fpvWidget.setVideoSource(FPVWidget.VideoSource.PRIMARY);
                secondaryFpvWidget.setVideoSource(FPVWidget.VideoSource.SECONDARY);
                break;
            case R.id.secondary_video_feed:
                fpvWidget.setVideoSource(FPVWidget.VideoSource.SECONDARY);
                secondaryFpvWidget.setVideoSource(FPVWidget.VideoSource.PRIMARY);
                break;
            case R.id.auto_video_feed:
                fpvWidget.setVideoSource(FPVWidget.VideoSource.AUTO);
                break;
            case R.id.change_size:
                resizeView();
                break;
        }

    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        switch (buttonView.getId()) {
            case R.id.checkboxPrimaryCameraName:
                fpvWidget.setSourceCameraNameVisibility(isChecked);
                break;
            case R.id.checkboxSecondaryCameraName:
                secondaryFpvWidget.setSourceCameraNameVisibility(isChecked);
                break;
            case R.id.checkbox_touch_focus:
                fpvOverlayWidget.setTouchFocusEnabled(isChecked);
                break;
            case R.id.checkbox_touch_metering:
                fpvOverlayWidget.setSpotMeteringEnabled(isChecked);
                break;
            case R.id.checkbox_gimbal_control:
                fpvOverlayWidget.setGimbalControlEnabled(isChecked);
                break;
        }

    }

    public void onInitPanoClick(View v){
        missionControl = MissionControl.getInstance();
        if (missionControl.scheduledCount() > 0) {
            missionControl.unscheduleEverything();
            missionControl.removeAllListeners();
        }

        int kind = Integer.parseInt(mEditText.getText().toString());
        boolean useGimbal = mCheckbox.isChecked();

        initPanorama(kind, useGimbal);
    }

    public void onStartPanoClick(View v){
        if (MissionControl.getInstance().scheduledCount() > 0) {
            MissionControl.getInstance().startTimeline();
        } else {

        }
    }

    public void onStopPanoClick(View v){
        MissionControl.getInstance().stopTimeline();
    }

    private void addGimbalAction(float pitch, float absoluteYaw) {
        // actually is relative YAW
        Attitude attitude = new Attitude(pitch, Rotation.NO_ROTATION, absoluteYaw);
        GimbalAttitudeAction gimbalAttitudeAction = new GimbalAttitudeAction(attitude);
        gimbalAttitudeAction.setGimbalMode(GimbalMode.FREE);
//        gimbalAttitudeAction.setCompletionTime(2);
        this.missionControl.scheduleElement(gimbalAttitudeAction);
    }

    private void addGimbalPitchAction(float pitch) {
        Attitude attitude = new Attitude(pitch, Rotation.NO_ROTATION, Rotation.NO_ROTATION);
        GimbalAttitudeAction gimbalAttitudeAction = new GimbalAttitudeAction(attitude);
        this.missionControl.scheduleElement(gimbalAttitudeAction);
    }

    private void addPhotoShootAction() {
        this.missionControl.scheduleElement(ShootPhotoAction.newShootSinglePhotoAction());
    }

    private void addAircraftYawActionv2(float absoluteYaw) {
        AircraftYawAction aircraftYawAction = new AircraftYawAction(absoluteYaw, true);
        this.missionControl.scheduleElement(aircraftYawAction);
    }

    public void initPanorama(int panoKind, boolean useGimbalYaw) {

        missionControl = MissionControl.getInstance();
        final TimelineEvent preEvent = null;
//        MissionControl.Listener listener = new MissionControl.Listener() {
//            @Override
//            public void onEvent(@Nullable TimelineElement element, TimelineEvent event, DJIError error) {
//                updateTimelineStatus(element, event, error);
//            }
//        };


        int[] pitchAngles;
        int[] numberOfPhotos;

        if (panoKind == 1){
            //72fov 15mm lens
            pitchAngles=new int[]{0, 29, 56, 90};
            numberOfPhotos=new int[]{9,8,5,3};
        }
        else if (panoKind == 3){ //45mm 146 25%
            pitchAngles=new int[]{ 0,    10,    21,    31,    41,    52,    62,    71,    81,    90};
            numberOfPhotos=new int[]{23,    23,    22,    20 ,   18,    14 ,   11 ,    8 ,    4 ,    3};
        }
        else if (panoKind == 4){ //45mm 171 30%
            pitchAngles=new int[]{  0,9,    19 ,   28 ,   37 ,   47 ,   56 ,   65 ,   74 ,   82 ,   90};
            numberOfPhotos=new int[]{25 ,   25 ,   23 ,   22 ,   20 ,   16 ,   13 ,   11 ,    7 ,    5  ,   4};
        }
        else if (panoKind == 2) { //45mm 15% 119
            pitchAngles=new int[]{    -5 ,    7   , 20  ,  32 ,   43 ,   55 ,   66  ,  78   , 90};
            numberOfPhotos=new int[]{ 21  ,  21    ,20    ,16  ,  14 ,   11   ,  8  ,   5 ,    3};
        }
        else if (panoKind == 6) { //dronepan 25%
            pitchAngles=new int[]{    -8 ,2 ,   13 ,   24 ,   35 ,   46 ,   57  ,  68 ,   79 ,   90};
            numberOfPhotos=new int[]{ 22  ,  22 ,   21 ,   20 ,   18 ,   15 ,   12 ,    8 ,    4  ,   1};
        }

        else if (panoKind == 5) { //dronepan 20%
            pitchAngles=new int[]{     -6 ,    6  ,  18 ,   30 ,   42  ,  54 ,   66 ,   78 ,   90};
            numberOfPhotos=new int[]{  21  ,  21   , 20   , 18 ,   16 ,   12  ,   9 ,    4 ,    1};
        }

        else
        {
            panoKind = -1;
            pitchAngles=new int[]{ 0,    10,    21,    31,    41,    52,    62,    71,    81,    90};
            numberOfPhotos=new int[]{23,    23,    22,    20 ,   18,    14 ,   11 ,    8 ,    4 ,    3};
        }

        Log.i("pano", "initPanorama: case"+ panoKind);




        float stepYaw;
        if (useGimbalYaw) {
            // set gimbal to freemode
            Attitude attitude = new Attitude(0, Rotation.NO_ROTATION, Rotation.NO_ROTATION);
            GimbalAttitudeAction gimbalAction = new GimbalAttitudeAction(attitude);
            gimbalAction.setGimbalMode(GimbalMode.FREE);
            this.missionControl.scheduleElement(gimbalAction);

            for (int i=0;i<pitchAngles.length;i++)
            {
//                this.addGimbalPitchAction(-pitchAngles[i]);
                stepYaw=360/numberOfPhotos[i];
                for (int j=0;j<numberOfPhotos[i];j++)
                {
                    float yaw = stepYaw*j;
                    if (i%2==0) //偶数行-180~180
                    {
                        this.addGimbalAction((float)-pitchAngles[i],-180F+yaw);
//                        this.addGimbalYawAction(-179+yaw);
                        Log.i("pano", "pitch:"+-pitchAngles[i]+" yaw:"+ (-180+yaw) );
                    }
                    else
                    {
                        this.addGimbalAction((float)-pitchAngles[i],180F-yaw);
//                        this.addGimbalYawAction(180-yaw);
                        Log.i("pano", "pitch:"+-pitchAngles[i]+" yaw:"+ (180-yaw) );
                    }
                    this.addPhotoShootAction();

                }

            }
        }
        else {
            //// TODO: 2017/6/2 uav yaw
            for (int i=0;i<pitchAngles.length;i++)
            {
                this.addGimbalPitchAction((float)(-pitchAngles[i]));
//                this.addGimbalPitchAction(-60);
                stepYaw=360/numberOfPhotos[i];
                for (int j=0;j<numberOfPhotos[i];j++)
                {
                    this.addPhotoShootAction();
                    float absoluteYaw = -180+stepYaw*j;
                    this.addAircraftYawActionv2(absoluteYaw);//absolute yaw -180,180
                }

            }
        }
//        missionControl.addListener(listener);

        //this.setupNadirShots();
//        this.notifyListener();
    }


}
