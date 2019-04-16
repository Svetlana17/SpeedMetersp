package fly.speedmeter.grub;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Time;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;

public class INS extends Activity implements SensorEventListener, View.OnClickListener {
    LinkedList<RawData> dataArray = new LinkedList<>();
    SensorManager manager;
    Button buttonStart;
    Button buttonStop, buttonGps;
    EditText editAlpha, editK, editTextShag;
    boolean isRunning;
    final String TAG = "SensorLog";
    FileWriter writer;
    Button shareButton;
    Timer timer;
    private SensorData data = new SensorData();
    Sensor sensorAccel, sensorGiros;
    StringBuilder sb = new StringBuilder();
    int v;
    long t;
    private float timestamp;
    private final float[] deltaRotationVector = new float[4];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ins);
        editTextShag = (EditText) findViewById(R.id.editShag);
        editTextShag.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                v = Integer.parseInt(editable.toString());
            }
        });
        shareButton = (Button) findViewById(R.id.buttonShare);
        shareButton.setOnClickListener(new View.OnClickListener() {


                                           @Override
                                           public void onClick(View v) {
                                               share();
                                           }
                                       }
        );
        isRunning = false;

        manager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        ///**


///

        buttonStart = (Button) findViewById(R.id.buttonStart);
        buttonStop = (Button) findViewById(R.id.buttonStop);
        editAlpha = (EditText) findViewById(R.id.editAlpha);
        editK = (EditText) findViewById(R.id.editK);
        buttonGps = (Button) findViewById(R.id.buttonGPS);
        buttonGps.setOnClickListener((View.OnClickListener) this);

        buttonStart.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                buttonStart.setEnabled(false);
                buttonStop.setEnabled(true);

                try {
                    float alpha = Float.parseFloat(editAlpha.getText().toString());
                    float k = Float.parseFloat(editK.getText().toString());

                    data = new SensorData();
                    data.setParams(alpha, k);
                } catch (NumberFormatException e) {
                    Toast.makeText(INS.this, "Данные введены не верно", Toast.LENGTH_LONG).show();
                }

                File file = new File(getStorageDir(), "sensors.csv");
                if (file.exists())
                    file.delete();

                Log.d(TAG, "Writing to " + getStorageDir());
                try {
                    writer = new FileWriter(file);

                    //writer.write("TIME;ACC X;ACC Y;ACC Z;ACC XF;ACC YF;ACC ZF;GYR X; GYR Y; GYR Z; GYR XF; GYR YF; GYR ZF;\n");
                    //    writer.write("TIME;ACC X;ACC Y;ACC Z;ACC XF;ACC YF;ACC ZF;GYR X; GYR Y; GYR Z; GYR XF; GYR YF; GYR ZF;  VX);
                    writer.write("TIME; dT; ACC X;ACC Y;ACC Z;ACC XF;ACC YF;ACC ZF;GYR X; GYR Y; GYR Z; GYR XF; GYR YF; GYR ZF;  VX; VY; VZ; VxFiltr;  VyFiltr; VzFiltr; Sx; Sy; Sz; SxF; SyF; SzF;" +
                            "XGirQ; UGirQ; ZGirQ; WGirQ f\n");

                } catch (IOException e) {
                    e.printStackTrace();
                }

                //     manager.registerListener(MainActivity.this, manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), v*1000);//было
                //      manager.registerListener(MainActivity.this, manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), v*1000);///было
                    manager.registerListener(INS.this, manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION), (int) v * 1000);//выносить
                manager.registerListener(INS.this, manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), (int) v * 1000);///
                isRunning = true;
                return true;
            }
        });

        buttonStop.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                buttonStart.setEnabled(true);
                buttonStop.setEnabled(false);
                isRunning = false;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    manager.flush(INS.this);
                }
                manager.unregisterListener(INS.this);
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return true;
            }
        });
    }

    private String getStorageDir() {
        return this.getExternalFilesDir(null).getAbsolutePath();
    }

    // @Override
    public void onFlushCompleted(Sensor sensor) {
    }

    @Override
    protected void onPause() {
        super.onPause();
        manager.unregisterListener(listener);
        timer.cancel();
    }

    String format(float values[]) {
        return String.format("%1$.1f\t\t%2$.1f\t\t%3$.1f", values[0], values[1],
                values[2]);
    }

    void showInfo() {
        sb.setLength(0);
        sb.append("Accelerometer: " + format(valuesAccel))
                .append("\n\nAccel motion: " + format(valuesAccel))
                .append("\nAccel gravity : " + format(valuesGiroscope));

//        tvText.setText(sb);
    }

    @Override
    protected void onResume() {
        super.onResume();
        manager.registerListener(listener, sensorAccel, (int) v);
        manager.registerListener(listener, sensorGiros, (int) v);
        timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //  showInfo();
                    }
                });
            }
        };

        timer.schedule(task, 0, 400);
    }

    float[] valuesAccel = new float[3];
    float[] valuesGiroscope = new float[3];

    boolean accelInited = false;
    boolean gyroInited = false;
    boolean firstIteration = true;

    SensorEventListener listener = new SensorEventListener() {

        @Override
        public void onSensorChanged(SensorEvent hardEvent) {
            long currentTime = System.currentTimeMillis();
            MySensorEvent event = new MySensorEvent(hardEvent, currentTime);
            long s = currentTime - t;
            if (firstIteration) {
                dataArray.add(new RawData(new double[]{0, 0, 0}, new double[]{0, 0, 0}, event.timestamp));
                firstIteration = false;
                return;
            }

            switch (event.sensor.getType()) {
                case Sensor.TYPE_LINEAR_ACCELERATION:
                    for (int i = 0; i < 3; i++) {
                        valuesAccel[0] = event.values[0];
                        valuesAccel[1] = event.values[1];
                        valuesAccel[2] = event.values[2];

                        try {
                            writer.write(data.getStringData(s));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        //  }
                        break;
                    }
                    accelInited = true;
                    break;
                case Sensor.TYPE_GYROSCOPE:
                    for (int i = 0; i < 3; i++) {
                        valuesGiroscope[i] = event.values[i];
                        data.setGyr(event);
                        // if (data.isAccDataExists()) {
                        try {
                            writer.write(data.getStringData(s));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        //  }
                        gyroInited = true;
                        break;
                    }
                    //учесть первую инициализацию значений аксел. и гироск.
                    if (!accelInited || !gyroInited) {
                        timestamp = event.timestamp;
                        return;
                    }
                    // dataArray.add(new RawData(valuesAccel, valuesGiroscope, event.timestamp));
                    timestamp = event.timestamp;
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {

        }

    };

    @Override
    public void onSensorChanged(SensorEvent event) {


        if (isRunning) {
            long currentTime = System.currentTimeMillis();
            if (t == 0) {
                t = currentTime;

            }
            MySensorEvent evt = new MySensorEvent(event, currentTime);
            long s = currentTime - t;
            try {
                switch (evt.sensor.getType()) {
                    case Sensor.TYPE_GYROSCOPE:
                        data.setGyr(evt);
                        if (data.isAccDataExists()) {
                            writer.write(data.getStringData(s));


                        }

                        break;
                    case Sensor.TYPE_LINEAR_ACCELERATION:
                        data.setAcc(evt);
                        if (data.isGyrDataExists()) {
                            writer.write(data.getStringData(s));

                        }
                        break;

                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    private void share() {
        File dir = getExternalFilesDir(null);
        File zipFile = new File(dir, "accel.zip");
        if (zipFile.exists()) {
            zipFile.delete();
        }
        File[] fileList = dir.listFiles();
        try {
            zipFile.createNewFile();
            ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFile));
            for (File file : fileList) {
                zipFile(out, file);
            }
            out.close();
            sendBundleInfo(zipFile);
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), "Can't send file!", Toast.LENGTH_LONG).show();
        }
    }

    private static void zipFile(ZipOutputStream zos, File file) throws IOException {
        zos.putNextEntry(new ZipEntry(file.getName()));
        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[10000];
        int byteCount = 0;
        try {
            while ((byteCount = fis.read(buffer)) != -1) {
                zos.write(buffer, 0, byteCount);
            }
        } finally {
            safeClose(fis);
        }
        zos.closeEntry();
    }

    private static void safeClose(FileInputStream fis) {
        try {
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendBundleInfo(File file) {
        Intent emailIntent = new Intent(Intent.ACTION_SEND);
        emailIntent.setType("message/rfc822");
        emailIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + file));
        startActivity(Intent.createChooser(emailIntent, "Send data"));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }

    class SensorData {
        private MySensorEvent gyrEvent, accEvent;
        private float xaf, yaf, zaf;
        private float xgf, ygf, zgf;
        private float alpha = 0.05f;
        private float k = 0.5f;
        float pxaf, pyaf, pzaf;

        float timestamp;
        private MySensorEvent prefaccEvent;
        private Time prefTime;///++
        private float vxfit, vyfit, vzfit;
        private float Sxfit, Syfit, Szfit;
        float vx, vy, vz, Sx, Sy, Sz;
        float alphaAcc, bettaAcc, gammaAcc;


        public void setParams(float alpha, float k) {
            this.alpha = alpha;
            this.k = k;
        }

        public void setGyr(MySensorEvent gyrEvent) {
            this.gyrEvent = gyrEvent;
        }

        public void setAcc(MySensorEvent accEvent) {
            this.prefaccEvent = this.accEvent;
            this.accEvent = accEvent;
            this.prefTime = prefTime;//++
        }

        public boolean isAccDataExists() {
            return accEvent != null;
        }

        public boolean isGyrDataExists() {
            return gyrEvent != null;
        }

        public void clear() {
            gyrEvent = null;
            accEvent = null;
        }

        public String getStringData(long date) {
            xaf = (alpha * accEvent.values[0]) + ((1 - alpha) * xaf);
            yaf = (alpha * accEvent.values[1]) + ((1 - alpha) * yaf);
            zaf = (alpha * accEvent.values[2]) + ((1 - alpha) * zaf);

            /// угол по акслерометру
            alphaAcc = (float) Math.atan(accEvent.values[0] / (sqrt((accEvent.values[1] * accEvent.values[1] + accEvent.values[2] * accEvent.values[2]))));
            bettaAcc = (float) Math.atan(accEvent.values[1] / (sqrt((accEvent.values[0] * accEvent.values[0] + accEvent.values[2] * accEvent.values[2]))));
            gammaAcc = (float) Math.atan(accEvent.values[2] / (sqrt((accEvent.values[0] * accEvent.values[0] + accEvent.values[1] * accEvent.values[1]))));


            // Комплементарный фильтр
            xgf = ((1 - k) * gyrEvent.values[0]) + (k * alphaAcc);
            ygf = ((1 - k) * gyrEvent.values[1]) + (k * bettaAcc);
            zgf = ((1 - k) * gyrEvent.values[2]) + (k * gammaAcc);
            float dT = 0;
            float dTS = 0;
            if (this.prefaccEvent != null) {
                dT = this.accEvent.time - this.prefaccEvent.time;
                dTS = (float) (dT / 1000.0); //сек Шаг
                /// if (timestamp != 0) {
                for (int index = 0; index < 3; ++index) ;
                {
                    if (dTS != 0 && accEvent.values != null) {
                        //Метод трапеций
                        vx = (float) ((((accEvent.values[0] + prefaccEvent.values[0])) / 2.0) * dTS);
                        Sx = vx * dTS;
                        vy = (float) (((accEvent.values[1] + prefaccEvent.values[1]) / 2.0) * dTS);
                        Sy = vy * dTS;
                        vz = ((float) ((accEvent.values[2] + prefaccEvent.values[2]) / 2.0) * dTS);
                        Sz = vz * dTS;
                        vxfit = (float) ((((xaf + pxaf)) / 2.0) * dTS);
                        Sxfit = vxfit * dTS;
                        vyfit = (float) (((yaf + pyaf) / 2.0) * dTS);
                        Syfit = vyfit * dTS;
                        vzfit = (float) (((zaf + pzaf) / 2.0) * dTS);
                        Szfit = vzfit * dTS;


                        float Sx_temp = Sx;
                        float Sy_temp = Sy;
                        float Sz_temp = Sz;
                    }
                }
            }
            // Расчет угловой скорости по гироскопу
            //  final float dT = (event.timestamp - timestamp) * NS2S;//+++
            float alpha, betta, gamma;
            float axisX = gyrEvent.values[0];//+++
            float axisY = gyrEvent.values[1];//+++
            float axisZ = gyrEvent.values[2];//+++
            // Calculate the angular speed of the sample
            float omegaMagnitude = (float) sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ);//+++
//            if (omegaMagnitude > EPSILON)
{//+++
                axisX /= omegaMagnitude;//+++
                axisY /= omegaMagnitude;//+++
                axisZ /= omegaMagnitude;//+++
                // Integrate around this axis with the angular speed by the timestep
                // in order to get a delta rotation from this sample over the timestep
                // We will convert this axis-angle representation of the delta rotation
                // into a quaternion before turning it into the rotation matrix.
            }
            float thetaOverTwo = omegaMagnitude * dTS / 2.0f;//+++
            float sinThetaOverTwo = (float) sin(thetaOverTwo);//+++
            float cosThetaOverTwo = (float) cos(thetaOverTwo);//+++
            deltaRotationVector[0] = sinThetaOverTwo * axisX;//+++
            deltaRotationVector[1] = sinThetaOverTwo * axisY;//+++
            deltaRotationVector[2] = sinThetaOverTwo * axisZ;//+++
            deltaRotationVector[3] = cosThetaOverTwo;//+++
            //Вычисление угла поворота по гироскопу
            float fiX = (float) (gyrEvent.values[0] * dTS);
            float fiY = (float) (gyrEvent.values[1] * dTS);
            float fiz = (float) (gyrEvent.values[2] * dTS);
            //  }
//            timestamp = gyrEvent.timestamp;//+++
//            float[] deltaRotationMatrix = new float[9];//+++
//            SensorManager.getRotationMatrixFromVector(deltaRotationMatrix, deltaRotationVector);//+++
//            // User code should concatenate the delta rotation we computed with the current rotation
//            // in order to get the updated rotation.
//            // rotationCurrent = rotationCurrent * deltaRotationMatrix;
            pxaf = xaf;
            pyaf = yaf;
            pzaf = zaf;
            return String.format(
                    "%d; " + " %f;" +
                            " %f; %f; %f;" +
                            " %f; %f; %f;" +
                            " %f; %f; %f;" +
                            " %f; %f; %f;" +
                            " %f; %f; %f; " +
                            " %f; %f; %f;" +
                            " %f; %f; %f;" +
                            " %f; %f; %f; %f;" +
                            " %f; %f; %f \n",
                    date, dTS,
                    accEvent.values[0], accEvent.values[1], accEvent.values[2],
                    xaf, yaf, zaf,
                    gyrEvent.values[0], gyrEvent.values[1], gyrEvent.values[2],
                    xgf, ygf, zgf,
                    vx, vy, vz,
                    vxfit, vyfit, vzfit,
                    Sx, Sy, Sz,
                    Sxfit, Syfit, Szfit,
                    deltaRotationVector[0],
                    deltaRotationVector[1],
                    deltaRotationVector[2],
                    deltaRotationVector[3]);
        }
    }

    public void onClick(View v) {
        Intent intent = new Intent(INS.this, MainActivity.class);
        startActivity(intent);
    }
}