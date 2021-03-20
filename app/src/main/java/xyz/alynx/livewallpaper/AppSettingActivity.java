package xyz.alynx.livewallpaper;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.widget.CompoundButton;
import android.widget.Switch;

/**
 * app setting activity
 * @author komine
 */
public class AppSettingActivity extends AppCompatActivity implements Switch.OnCheckedChangeListener {
    private Switch swAllowSlide;
    private Switch swAllVolume;
    private Switch swAutoSwitch;
    private Switch swDoubleSwitch;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_app_setting_activity);

        init();
        initView();
        initSwitchCheckValue();
        initEvent();
    }
    private void init(){
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null){
            actionBar.setTitle(R.string.action_setting);
        }
    }

    private void initView(){
        swAllowSlide = findViewById(R.id.swAllowSlide);
        swAllVolume = findViewById(R.id.swAllowVolume);
        swAutoSwitch = findViewById(R.id.swAutoSwitch);
        swDoubleSwitch = findViewById(R.id.swDoubleSwitch);
    }

    private void initEvent(){
        swAutoSwitch.setOnCheckedChangeListener(this);
        swAllVolume.setOnCheckedChangeListener(this);
        swAllowSlide.setOnCheckedChangeListener(this);
        swDoubleSwitch.setOnCheckedChangeListener(this);
    }

    private void initSwitchCheckValue(){
        SharedPreferences preferences = getSharedPreferences(LWApplication.OPTIONS_PREF, LWApplication.MODE_PRIVATE);
        swAllowSlide.setChecked(preferences.getBoolean(LWApplication.SLIDE_WALLPAPER_KEY,false));
        swAutoSwitch.setChecked(preferences.getBoolean(AppConfig.ALLOW_AUTO_SWITCH,false));
        swAllVolume.setChecked(preferences.getBoolean(AppConfig.ALLOW_VOLUME,false));
        swDoubleSwitch.setChecked(preferences.getBoolean(AppConfig.DOUBLE_SWITCH,true));
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        switch (buttonView.getId()){
            case R.id.swAllowSlide:
                AppConfig.setAllowSlide(buttonView.isChecked());
                break;
            case R.id.swAutoSwitch:
                AppConfig.setAllowAutoSwitch(buttonView.isChecked());
                break;
            case R.id.swAllowVolume:
                AppConfig.setAllowVolume(buttonView.isChecked());
                break;
            case R.id.swDoubleSwitch:
                AppConfig.setDoubleSwitch(buttonView.isChecked());
                break;
            default:
                break;
        }
        SharedPreferences.Editor editor = getSharedPreferences(LWApplication.OPTIONS_PREF, LWApplication.MODE_PRIVATE).edit();
        editor.putBoolean(LWApplication.SLIDE_WALLPAPER_KEY,swAllowSlide.isChecked());
        editor.putBoolean(AppConfig.ALLOW_AUTO_SWITCH,swAutoSwitch.isChecked());
        editor.putBoolean(AppConfig.ALLOW_VOLUME,swAllVolume.isChecked());
        editor.putBoolean(AppConfig.DOUBLE_SWITCH,swDoubleSwitch.isChecked());
        editor.apply();
    }
}
