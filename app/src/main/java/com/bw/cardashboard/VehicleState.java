package com.bw.cardashboard;

/**
 * 车辆实时状态快照与 60 秒波形缓冲（100% 匹配实车 MCU 有效 CAN 报文）。
 */
public class VehicleState {

    public static final int HISTORY_SIZE = 60;

    public boolean connected = false;
    public boolean demoMode = false;

    // 1. 车速与挡位
    public float speedKmh = 0f;
    public float sessionMaxSpeedKmh = 0f;
    public float longitudinalG = 0f;
    public float prevSpeedMs = -1f;
    public long prevSpeedTimeMs = 0L;
    public int gear = 0;

    // 2. 能源与续航
    public float socPercent = 0f;
    public float evRangeKm = 0f;
    public int fuelPercent = 0;
    public int fuelRangeKm = 0;
    public float avgEleKwh = 16.0f;
    public float avgOilL = 1.7f;

    // 3. 高压母线电压
    public float hvVoltageV = 0f;
    public float sessionMinHvV = 999f;
    public float sessionMaxHvV = 0f;

    // 4. 方向盘与行程/保养
    public float steeringAngleDeg = 0f;
    public int tripSeconds = 0;
    public int maintRemainKm = 0;

    // 5. 环境与空气质量
    public int pm25Inside = 0;
    public int pm25Outside = 0;
    public float outsideTempC = 0f;
    public float solarIntensity = 0f;

    // 6. 60s 波形缓冲（车速 + 母线电压）
    public final float[] speedHistory = new float[HISTORY_SIZE];
    public final float[] voltageHistory = new float[HISTORY_SIZE];
    public int historyCount = 0;

    public synchronized void pushHistoryPoint() {
        if (historyCount < HISTORY_SIZE) {
            speedHistory[historyCount] = speedKmh;
            voltageHistory[historyCount] = hvVoltageV;
            historyCount++;
        } else {
            System.arraycopy(speedHistory, 1, speedHistory, 0, HISTORY_SIZE - 1);
            System.arraycopy(voltageHistory, 1, voltageHistory, 0, HISTORY_SIZE - 1);
            speedHistory[HISTORY_SIZE - 1] = speedKmh;
            voltageHistory[HISTORY_SIZE - 1] = hvVoltageV;
        }
    }

    public synchronized void resetSessionRecords() {
        sessionMaxSpeedKmh = speedKmh;
        if (hvVoltageV > 50f) {
            sessionMinHvV = hvVoltageV;
            sessionMaxHvV = hvVoltageV;
        } else {
            sessionMinHvV = 999f;
            sessionMaxHvV = 0f;
        }
    }

    public synchronized VehicleState copy() {
        VehicleState c = new VehicleState();
        c.connected = connected;
        c.demoMode = demoMode;
        c.speedKmh = speedKmh;
        c.sessionMaxSpeedKmh = sessionMaxSpeedKmh;
        c.longitudinalG = longitudinalG;
        c.gear = gear;
        c.socPercent = socPercent;
        c.evRangeKm = evRangeKm;
        c.fuelPercent = fuelPercent;
        c.fuelRangeKm = fuelRangeKm;
        c.avgEleKwh = avgEleKwh;
        c.avgOilL = avgOilL;
        c.hvVoltageV = hvVoltageV;
        c.sessionMinHvV = sessionMinHvV;
        c.sessionMaxHvV = sessionMaxHvV;
        c.steeringAngleDeg = steeringAngleDeg;
        c.tripSeconds = tripSeconds;
        c.maintRemainKm = maintRemainKm;
        c.pm25Inside = pm25Inside;
        c.pm25Outside = pm25Outside;
        c.outsideTempC = outsideTempC;
        c.solarIntensity = solarIntensity;
        System.arraycopy(speedHistory, 0, c.speedHistory, 0, HISTORY_SIZE);
        System.arraycopy(voltageHistory, 0, c.voltageHistory, 0, HISTORY_SIZE);
        c.historyCount = historyCount;
        return c;
    }

    public static String formatGear(int rawGear) {
        if (rawGear == 4 || rawGear == 0) return "P";
        if (rawGear == 2) return "R";
        if (rawGear == 1) return "N";
        if (rawGear == 8 || rawGear == 3) return "D";
        if (rawGear == 16 || rawGear == 32) return "S";
        return "D";
    }
}
