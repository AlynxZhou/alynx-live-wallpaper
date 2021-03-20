package xyz.alynx.livewallpaper;

public class AppConfig {
    private static boolean allowSlide = false;
    private static boolean allowAutoSwitch = false;
    private static boolean allowVolume = false;
    private static boolean doubleSwitch = true;
    private static boolean isChange = false;
    public static final String ALLOW_VOLUME = "allow_volume";
    public static final String ALLOW_AUTO_SWITCH = "allow_auto_switch";
    public static final String DOUBLE_SWITCH = "double_switch";

    public static void setIsChange(boolean isChange) {
        AppConfig.isChange = isChange;
    }

    public static void setAllowAutoSwitch(boolean allowAutoSwitch) {
        AppConfig.allowAutoSwitch = allowAutoSwitch;
    }

    public static void setAllowSlide(boolean allowSlide) {
        AppConfig.allowSlide = allowSlide;
    }

    public static void setAllowVolume(boolean allowVolume) {
        AppConfig.allowVolume = allowVolume;
    }

    public static void setDoubleSwitch(boolean doubleSwitch) {
        AppConfig.doubleSwitch = doubleSwitch;
    }

    static boolean isAllowAutoSwitch() {
        return allowAutoSwitch;
    }

    public static boolean isAllowSlide() {
        return allowSlide;
    }

    static boolean isAllowVolume() {
        return allowVolume;
    }

    static boolean isDoubleSwitch() {
        return doubleSwitch;
    }

    public static boolean isIsChange() {
        return isChange;
    }
}
