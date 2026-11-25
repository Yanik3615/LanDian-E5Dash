package com.bw.cardashboard;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 蓝电 E5 (DFSK-S508L) bw.car.Car CAN 数据采集桥接层（V2 实车有效报文精简版）。
 */
public class BwCarClient {

    public interface Listener {
        void onStateUpdated(VehicleState snapshot);
    }

    // 实车已验证有实时推送的 CAN Property IDs
    private static final int PERF_VEHICLE_SPEED = 0x11600207;           // Float (m/s -> *3.6 km/h)
    private static final int GEAR_SELECTION = 0x11400400;               // Int
    private static final int EV_BATTERY_PERCENT = 0x1160030d;           // Float (%)
    private static final int VEHICLE_EV_RANGE_REMAINING = 0x21605858;   // Float (km)

    private static final int VEHICLE_FUEL_REMAINING_PERCENT = 0x21405978; // Int (%)
    private static final int VEHICLE_FUEL_ODOMETER = 0x21405843;          // Int (km)

    private static final int VEHICLE_ENGINE_GCU_VBUS = 0x2140597f;        // Int (V)
    private static final int VEHICLE_STEERING_ANGLE = 0x21405841;         // Int ((raw-7800)*0.1 deg)

    private static final int VEHICLE_CURRENT_TRIP_LAST_TIME = 0x214059b3; // Int (s)
    private static final int VEHICLE_MAINTENANCE_MILEAGE = 0x21405842;    // Int (km)

    private static final int HVAC_INSIDE_PM25_VALUE = 0x21400524;         // Int
    private static final int HVAC_OUTSIDE_PM25_VALUE = 0x21400525;        // Int
    private static final int ENV_OUTSIDE_TEMPERATURE = 0x11600703;        // Float (℃)
    private static final int VEHICLE_SOLAR_INTENSITY = 0x28605854;        // Float

    private static final int[] ALL_PROPS = new int[]{
            PERF_VEHICLE_SPEED, GEAR_SELECTION, EV_BATTERY_PERCENT, VEHICLE_EV_RANGE_REMAINING,
            VEHICLE_FUEL_REMAINING_PERCENT, VEHICLE_FUEL_ODOMETER,
            VEHICLE_ENGINE_GCU_VBUS, VEHICLE_STEERING_ANGLE,
            VEHICLE_CURRENT_TRIP_LAST_TIME, VEHICLE_MAINTENANCE_MILEAGE,
            HVAC_INSIDE_PM25_VALUE, HVAC_OUTSIDE_PM25_VALUE,
            ENV_OUTSIDE_TEMPERATURE, VEHICLE_SOLAR_INTENSITY
    };

    private static volatile BwCarClient instance;

    private final Context appContext;
    private final VehicleState state = new VehicleState();
    private final TelemetryLogger logger;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Object carObj;
    private Object propertyManagerObj;
    private Object listenerProxyObj;
    private boolean started = false;
    private int demoTick = 0;

    public static BwCarClient getInstance(Context context) {
        if (instance == null) {
            synchronized (BwCarClient.class) {
                if (instance == null) {
                    instance = new BwCarClient(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private BwCarClient(Context context) {
        this.appContext = context;
        this.logger = new TelemetryLogger(context);
    }

    public TelemetryLogger getLogger() {
        return logger;
    }

    public void addListener(Listener l) {
        if (!listeners.contains(l)) {
            listeners.add(l);
            l.onStateUpdated(state.copy());
        }
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public void resetSessionRecords() {
        state.resetSessionRecords();
        notifyListeners();
    }

    public synchronized void start() {
        if (started) return;
        started = true;
        connectBwCar();
        mainHandler.post(tick1HzRunnable);
    }

    private static String xd(int... a) {
        char[] c = new char[a.length];
        for (int i = 0; i < a.length; i++) {
            c[i] = (char) (a[i] ^ (0x5A + (i % 7)));
        }
        return new String(c);
    }

    private void connectBwCar() {
        try {
            Class<?> carClass = Class.forName(xd(56,44,114,62,63,45,78,25,58,46));
            Method createCar = carClass.getMethod(xd(57,41,57,60,42,58,35,59,41), Context.class, ServiceConnection.class);
            carObj = createCar.invoke(null, appContext, new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    onCarConnected();
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    synchronized (state) {
                        state.connected = false;
                    }
                }
            });
            if (carObj != null) {
                carClass.getMethod(xd(57,52,50,51,59,60,20)).invoke(carObj);
            } else {
                enableDemoMode();
            }
        } catch (Throwable t) {
            enableDemoMode();
        }
    }

    private void enableDemoMode() {
        synchronized (state) {
            state.demoMode = true;
            state.connected = false;
        }
    }

    private void onCarConnected() {
        try {
            Class<?> carClass = Class.forName(xd(56,44,114,62,63,45,78,25,58,46));
            propertyManagerObj = carClass.getMethod(xd(61,62,40,30,63,45,45,59,53,61,58,59,45), String.class)
                    .invoke(carObj, xd(42,41,51,45,59,45,20,35));
            if (propertyManagerObj == null) {
                enableDemoMode();
                return;
            }

            synchronized (state) {
                state.connected = true;
                state.demoMode = false;
            }

            Class<?> pmClass = propertyManagerObj.getClass();
            Class<?> listenerIf = Class.forName(xd(56,44,114,62,63,45,78,50,58,46,57,41,62,18,63,117,44,47,49,47,5,40,47,37,115,29,62,18,10,41,51,45,59,45,20,35,22,61,51,63,56,5,40,127,31,60,44,15,18,53,43,57,47,42,38,37,44,62,50,41,18,54,19,46,62,50,56,44));

            listenerProxyObj = Proxy.newProxyInstance(
                    listenerIf.getClassLoader(),
                    new Class<?>[]{listenerIf},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) {
                            String name = method.getName();
                            if (xd(53,53,31,53,63,49,7,63,30,42,56,48,43).equals(name) && args != null && args.length == 1) {
                                handlePropertyEvent(args[0]);
                                return null;
                            }
                            if ("hashCode".equals(name)) return System.identityHashCode(proxy);
                            if ("equals".equals(name)) return args != null && args.length == 1 && proxy == args[0];
                            if ("toString".equals(name)) return "P";
                            return null;
                        }
                    }
            );

            Method regMethod = pmClass.getMethod(xd(40,62,59,52,45,43,5,40,23,53,46,42,58,14,63,41), listenerIf, int.class, float.class);
            Method getPropMethod = pmClass.getMethod(xd(61,62,40,13,44,48,16,63,41,40,36), int.class, int.class);

            for (int propId : ALL_PROPS) {
                try {
                    boolean ok = (Boolean) regMethod.invoke(propertyManagerObj, listenerProxyObj, propId, 0.0f);
                    if (!ok) {
                        regMethod.invoke(propertyManagerObj, listenerProxyObj, propId, 10.0f);
                    }
                } catch (Throwable t) {
                    try {
                        regMethod.invoke(propertyManagerObj, listenerProxyObj, propId, 10.0f);
                    } catch (Throwable t2) {
                        Log.w("BwCarClient", "register fail 0x" + Integer.toHexString(propId));
                    }
                }

                try {
                    int area = (propId == VEHICLE_SOLAR_INTENSITY) ? 1 : 0;
                    Object valObj = getPropMethod.invoke(propertyManagerObj, propId, area);
                    if (valObj != null) handlePropertyEvent(valObj);
                } catch (Throwable ignored) {
                }
            }
            readSystemSettingsValues();
        } catch (Throwable t) {
            Log.e("BwCarClient", "onCarConnected error", t);
            enableDemoMode();
        }
    }

    private void readSystemSettingsValues() {
        try {
            String eleStr = Settings.System.getString(appContext.getContentResolver(), xd(56,44,3,52,61,0,1,44,60,3,24,18,26));
            if (eleStr != null && !eleStr.isEmpty()) {
                synchronized (state) {
                    state.avgEleKwh = Float.parseFloat(eleStr);
                }
            }
            String oilStr = Settings.System.getString(appContext.getContentResolver(), xd(56,44,3,52,61,0,1,44,60,3,50,55,51));
            if (oilStr != null && !oilStr.isEmpty()) {
                String[] parts = oilStr.split(",");
                float last = Float.parseFloat(parts[parts.length - 1].trim());
                synchronized (state) {
                    state.avgOilL = last;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void handlePropertyEvent(Object carPropValue) {
        if (carPropValue == null) return;
        try {
            Class<?> cls = carPropValue.getClass();
            int propId = (Integer) cls.getMethod(xd(61,62,40,13,44,48,16,63,41,40,36,23,59)).invoke(carPropValue);
            Object val = cls.getMethod(xd(61,62,40,11,63,51,21,63)).invoke(carPropValue);
            if (val == null) return;

            synchronized (state) {
                applyProperty(propId, val);
            }
        } catch (Throwable ignored) {
        }
    }

    private void applyProperty(int propId, Object val) {
        switch (propId) {
            case PERF_VEHICLE_SPEED: {
                float ms = Math.max(0f, asFloat(val));
                float kmh = ms * 3.6f;
                long now = System.currentTimeMillis();
                if (kmh < 1.0f || state.gear == 4) {
                    state.longitudinalG = 0f;
                } else if (state.prevSpeedTimeMs > 0L && state.prevSpeedMs >= 0f) {
                    long dtMs = now - state.prevSpeedTimeMs;
                    if (dtMs >= 80L && dtMs <= 3000L) {
                        float instantG = ((ms - state.prevSpeedMs) / (dtMs / 1000f)) / 9.81f;
                        instantG = clamp(instantG, -1.2f, 1.2f);
                        state.longitudinalG = state.longitudinalG * 0.45f + instantG * 0.55f;
                        if (Math.abs(state.longitudinalG) < 0.015f) {
                            state.longitudinalG = 0f;
                        }
                    } else if (dtMs > 3000L) {
                        state.longitudinalG = 0f;
                    }
                }
                state.prevSpeedMs = ms;
                state.prevSpeedTimeMs = now;
                state.speedKmh = kmh;
                if (kmh > state.sessionMaxSpeedKmh) {
                    state.sessionMaxSpeedKmh = kmh;
                }
                break;
            }
            case GEAR_SELECTION:
                state.gear = asInt(val);
                break;
            case EV_BATTERY_PERCENT:
                state.socPercent = clamp(asFloat(val), 0f, 100f);
                break;
            case VEHICLE_EV_RANGE_REMAINING:
                state.evRangeKm = Math.max(0f, asFloat(val));
                break;
            case VEHICLE_FUEL_REMAINING_PERCENT:
                state.fuelPercent = Math.round(clamp(asFloat(val), 0f, 100f));
                break;
            case VEHICLE_FUEL_ODOMETER:
                state.fuelRangeKm = Math.max(0, asInt(val));
                break;
            case VEHICLE_ENGINE_GCU_VBUS: {
                float v = asFloat(val);
                if (v > 1000f) v /= 10f;
                if (v > 50f) {
                    state.hvVoltageV = v;
                    if (v < state.sessionMinHvV) state.sessionMinHvV = v;
                    if (v > state.sessionMaxHvV) state.sessionMaxHvV = v;
                }
                break;
            }
            case VEHICLE_STEERING_ANGLE: {
                float raw = asFloat(val);
                // 实车方向盘传感器：数值小于 7800 为右转、大于 7800 为左转，需取反使左转为负、右转为正
                state.steeringAngleDeg = (raw > 1000f) ? (7800f - raw) * 0.1f : -raw;
                break;
            }
            case VEHICLE_CURRENT_TRIP_LAST_TIME:
                // 原车 0x214059b3 实际为上一次行程静态时长，行驶中不会递增，改由 tick1HzRunnable 实时累计
                break;
            case VEHICLE_MAINTENANCE_MILEAGE:
                state.maintRemainKm = Math.max(0, asInt(val));
                break;
            case HVAC_INSIDE_PM25_VALUE:
                state.pm25Inside = Math.max(0, asInt(val));
                break;
            case HVAC_OUTSIDE_PM25_VALUE:
                state.pm25Outside = Math.max(0, asInt(val));
                break;
            case ENV_OUTSIDE_TEMPERATURE:
                state.outsideTempC = asFloat(val);
                break;
            case VEHICLE_SOLAR_INTENSITY:
                state.solarIntensity = Math.max(0f, asFloat(val));
                break;
            default:
                break;
        }
    }

    private float asFloat(Object o) {
        return (o instanceof Number) ? ((Number) o).floatValue() : 0f;
    }

    private int asInt(Object o) {
        return (o instanceof Number) ? ((Number) o).intValue() : 0;
    }

    private float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private final Runnable tick1HzRunnable = new Runnable() {
        @Override
        public void run() {
            VehicleState snap;
            synchronized (state) {
                if (state.demoMode) {
                    advanceDemoState();
                } else {
                    long now = System.currentTimeMillis();
                    if (state.speedKmh < 1.0f || state.gear == 4 || (state.prevSpeedTimeMs > 0L && now - state.prevSpeedTimeMs > 1500L)) {
                        state.longitudinalG = 0f;
                    }
                    if (state.connected && (state.speedKmh >= 0.5f || (state.gear != 0 && state.gear != 4))) {
                        state.tripSeconds++;
                    }
                    if (demoTick % 10 == 0) {
                        readSystemSettingsValues();
                    }
                }
                demoTick++;
                state.pushHistoryPoint();
                snap = state.copy();
            }
            if (snap.connected || snap.demoMode) {
                logger.recordTick(snap);
            }
            notifyListeners();
            mainHandler.postDelayed(this, 1000);
        }
    };

    private void notifyListeners() {
        VehicleState snap = state.copy();
        for (Listener l : listeners) {
            l.onStateUpdated(snap);
        }
    }

    private void advanceDemoState() {
        double phase = demoTick * 0.18;
        state.speedKmh = (float) (58.0 + 18.0 * Math.sin(phase * 0.7));
        if (state.speedKmh > state.sessionMaxSpeedKmh) state.sessionMaxSpeedKmh = state.speedKmh;
        state.gear = 8;
        state.socPercent = 25.1f;
        state.evRangeKm = 12f;
        state.fuelPercent = 23;
        state.fuelRangeKm = 195;
        state.hvVoltageV = (float) (347.0 - 5.0 * Math.sin(phase * 0.7));
        if (state.hvVoltageV < state.sessionMinHvV) state.sessionMinHvV = state.hvVoltageV;
        if (state.hvVoltageV > state.sessionMaxHvV) state.sessionMaxHvV = state.hvVoltageV;
        state.steeringAngleDeg = (float) (18.0 * Math.sin(phase * 0.5));
        state.tripSeconds = 3200 + demoTick;
        state.maintRemainKm = 2118;
        state.pm25Inside = 36;
        state.pm25Outside = 124;
        state.outsideTempC = 25.0f;
        state.solarIntensity = 56.3f;
    }
}
