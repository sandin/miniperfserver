package com.github.sandin.miniperfserver.data;

public final class DataSource {
    public static String[] sCpuTemperatureSystemFilePaths = {
            "/sys/kernel/debug/tegra_thermal/temp_tj",
            "/sys/devices/platform/s5p-tmu/curr_temp",
            "/sys/devices/virtual/thermal/thermal_zone1/temp",//常用
            "/sys/devices/system/cpu/cpufreq/cput_attributes/cur_temp",
            "/sys/devices/virtual/hwmon/hwmon2/temp1_input",
            "/sys/devices/platform/coretemp.0/temp2_input",
            "/sys/devices/virtual/thermal/thermal_zone0/temp",
            "/sys/devices/system/cpu/cpu0/cpufreq/cpu_temp",
            "/sys/class/thermal/thermal_zone7/temp",
            "/sys/devices/platform/omap/omap_temp_sensor.0/temperature",
            "/sys/class/thermal/thermal_zone1/temp",
            "/sys/devices/virtual/thermal/thermal_zone7/temp",
            "/sys/devices/platform/s5p-tmu/temperature",
            "/sys/devices/w1 bus master/w1_master_attempts",
            "/sys/class/thermal/thermal_zone0/temp"
    };

    public static String[] sCurrentSystemFilePaths = {
            "/sys/class/power_supply/battery/current_now",
//            "/sys/class/power_supply/battery/batt_current_now",
//            "/sys/class/power_supply/battery/batt_current"
    };

    public static String[] sVoltageSystemFilePaths = {
            "/sys/class/power_supply/battery/voltage_now"
    };

    public static String[] sGpuUsageSystemFilePaths = {
            "/sys/class/kgsl/kgsl-3d0/gpubusy",//高通
    };

    public static String[] sGpuClockSystemFilePaths = {
            "/sys/class/kgsl/kgsl-3d0/gpuclk",//高通
    };


}
