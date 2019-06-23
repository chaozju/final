// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.codelab.mlkit;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.ml.common.FirebaseMLException;
import com.google.firebase.ml.custom.FirebaseModelDataType;
import com.google.firebase.ml.custom.FirebaseModelInputOutputOptions;
import com.google.firebase.ml.custom.FirebaseModelInputs;
import com.google.firebase.ml.custom.FirebaseModelInterpreter;
import com.google.firebase.ml.custom.FirebaseModelManager;
import com.google.firebase.ml.custom.FirebaseModelOptions;
import com.google.firebase.ml.custom.FirebaseModelOutputs;
import com.google.firebase.ml.custom.model.FirebaseLocalModelSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import android.util.SparseIntArray;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureRequest.Builder;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;

public class MainActivity extends AppCompatActivity  {
    private static final String TAG = "MainActivity";

    private CameraManager mCameraManager;
    private String mCameraId;
    private CameraDevice mCameraDevice;
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private Builder mPreviewRequestBuilder;
    private CameraCaptureSession mCaptureSession;
    private CaptureRequest mPreviewRequest;
    private Handler mBackgroundHandler;

    private TextView mText;
    private Button captureButton;

    private Bitmap[] mBitmaps;
    private int mIndex = 0;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mText=(TextView)findViewById(R.id.textView);
        mText.setText("Begin");

        mBitmaps = new Bitmap[8];

        captureButton = (Button)findViewById(R.id.button);
        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mIndex=0;
                mText.setText("Begin");
                takePicture();

            }
        });

        // 获取 CameraManager 对象
        mCameraManager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);

        // 获取 HandlerThread 对象保障线程安全
        HandlerThread handlerThread = new HandlerThread("Camera2");
        handlerThread.start();
        mBackgroundHandler = new Handler(handlerThread.getLooper());




        // 创建 SurfaceView 对象，匹配对应控件
        mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        // 获得 SurfaceHolder 对象
        mSurfaceHolder = mSurfaceView.getHolder();

        mSurfaceHolder.setFormat(PixelFormat.TRANSPARENT);

        mSurfaceHolder.addCallback(mSurfaceCallback);


        initCustomModel();
    }



    //添加 CameraDevice.StateCallback 对相机状态进行监听，以便在相机状态发生改变的时候做相应的操作
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            // 创建CameraPreviewSession
            createCameraPreviewSession();

        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {

            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            cameraDevice.close();
            mCameraDevice = null;
        }

    };

    private final CameraCaptureSession.CaptureCallback mCaptureListener = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session,
                                       CaptureRequest request,
                                       TotalCaptureResult result) {
            try{
                mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(),this,mBackgroundHandler);
            }
            catch (CameraAccessException e){

            }
            catch (IllegalStateException e){

            }
        }
    };

    protected void takePicture() {
        if(null == mCameraDevice) {
            Log.e(TAG, "mCameraDevice is null");
            return;
        }
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraDevice.getId());
            Size[] jpegSizes = null;
            if (characteristics != null) {
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            }
            int width = 640;
            int height = 480;
            if (jpegSizes != null && 0 < jpegSizes.length) {
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }
            ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            List<Surface> outputSurfaces = new ArrayList<Surface>(2);
            outputSurfaces.add(reader.getSurface());

            final CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation)+180);

            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = null;
                    try {
                        image = reader.acquireNextImage();
//                        try {
//                            Thread.sleep(200);
//                        }
//                        catch (InterruptedException e){
//                            e.printStackTrace();
//                        }
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
//                        色彩模式默认值是ARGB_8888
                        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length,null);

                        bitmap = Bitmap.createScaledBitmap(bitmap, DIM_IMG_SIZE_X, DIM_IMG_SIZE_Y, true);
                        mBitmaps[mIndex] = bitmap;
                        Log.e(TAG,"Captured No."+Integer.toString(mIndex));
                        mIndex++;
                    }
                    catch (ArrayIndexOutOfBoundsException e){

                    }
                    finally {
                        if (image != null) {
                            image.close();
                        }
                    }
                }
            };
            reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);
            while(mIndex<8){
                try{
                    Thread.sleep(100);
                    mCameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(CameraCaptureSession session) {
                        try {
                            session.capture(captureBuilder.build(), mCaptureListener, mBackgroundHandler);
                            Log.e(TAG,"Sent request for No."+Integer.toString(mIndex));
//                            mText.append("\n采样中...No "+ Integer.toString(mIndex));
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        } catch (IllegalStateException e){

                        }
                    }
                    @Override
                    public void onConfigureFailed(CameraCaptureSession session) {
                    }
                }, mBackgroundHandler);
                }
                catch (InterruptedException e)
                {
                    Log.e(TAG,e.getMessage());
                }catch (IllegalStateException e){
                    Log.e(TAG,e.getMessage());

                }
            }
            {
                runModelInference();
//                mIndex=0;
            }
            createCameraPreviewSession();

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // 创建CameraPreviewSession
    private void createCameraPreviewSession() {


        try {
            //设置了一个具有输出Surface的CaptureRequest.Builder。
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(mSurfaceHolder.getSurface());

//            mState = STATE_PREVIEW;
            //创建一个CameraCaptureSession来进行相机预览。
            mCameraDevice.createCaptureSession(Arrays.asList(mSurfaceHolder.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // 相机已经关闭
                            if (null == mCameraDevice) {
                                return;
                            }
                            // 会话准备好后，我们开始显示预览
                            mCaptureSession = cameraCaptureSession;
                            try {
                                // 自动对焦
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                                Log.e(TAG," 正在开启相机预览并添加事件");
//                                mText.append("\n正在开启相机预览并添加事件");


                                // 开启相机预览并添加事件
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                //发送请求
//                                if(isRepeating == false){
                                    mCaptureSession.stopRepeating();
                                    mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                            new CameraCaptureSession.CaptureCallback() {
                                                @Override
                                                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, TotalCaptureResult result) {

                                                }

                                                @Override
                                                public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {

                                                }
                                            }, mBackgroundHandler);

                                    Log.e(TAG," 开启相机预览并添加事件");
//                                    isRepeating=true;
//                                }

                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            } catch (IllegalStateException e){
                                Log.e(TAG,e.getMessage());

                            }

                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            Log.e(TAG," onConfigureFailed 开启预览失败");
                        }
                    }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG," CameraAccessException 开启预览失败");
            e.printStackTrace();
        }
    }

    private SurfaceHolder.Callback mSurfaceCallback = new SurfaceHolder.Callback() {

        /**
         *  在 Surface 首次创建时被立即调用：获得焦点时
         *  一般在这里开启画图的线程
         * @param surfaceHolder 持有当前 Surface 的 SurfaceHolder 对象
         */
        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder) {
            // 通过 CameraManager 对象获取可用相机的cameraId
            try{
                for(String cameraId : mCameraManager.getCameraIdList()){
                    CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(cameraId);
                    Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                    if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                        continue;
                    }
                    StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    if (map == null) {
                        continue;
                    }
                    mCameraId = cameraId;
                    Log.e(TAG," 相机可用 ");
//                return;
                }
            }catch (CameraAccessException e) {
                e.printStackTrace();
            }catch (NullPointerException e) {
                //不支持Camera2API
            }

            //尝试打开相机
            try {
                mCameraManager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
            } catch (CameraAccessException e) {
                Log.e(TAG," 相机无法打开 ");
                e.printStackTrace();
            }/* catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }*/

        }

        @Override
        public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {

        }


        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        }
    };

//    private static final String LOCAL_MODEL_ASSET = "Gesture.tflite";
    private static final String LOCAL_MODEL_ASSET = "final_.tflite";

    private static final String LABEL_PATH = "labels";
    private static final int RESULTS_TO_SHOW = 3;
    /**
     * Dimensions of inputs.
     */
    private static final int DIM_BATCH_SIZE = 8;
    private static final int DIM_PIXEL_SIZE = 3;
    private static final int DIM_IMG_SIZE_X = 224;
    private static final int DIM_IMG_SIZE_Y = 224;
    private static final int IMAGE_MEAN = 128;
    private static final float IMAGE_STD = 255.0f;
    /**
     * Labels corresponding to the output of the vision model.
     */
    private List<String> mLabelList;

    private final PriorityQueue<Map.Entry<String, Float>> sortedLabels =
            new PriorityQueue<>(
                    RESULTS_TO_SHOW,
                    new Comparator<Map.Entry<String, Float>>() {
                        @Override
                        public int compare(Map.Entry<String, Float> o1, Map.Entry<String, Float>
                                o2) {
                            return (o1.getValue()).compareTo(o2.getValue());
                        }
                    });
    /* Preallocated buffers for storing image data. */
    private final int[][] intValues = new int[DIM_BATCH_SIZE ][ DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y];

    /**
     * An instance of the driver class to run model inference with Firebase.
     */
    private FirebaseModelInterpreter mInterpreter;
    /**
     * Data configuration of input & output data of model.
     */
    private FirebaseModelInputOutputOptions mDataOptions;



    private void initCustomModel() {
        mLabelList = loadLabelList(this);

        int[] inputDims = { 1, DIM_IMG_SIZE_X, DIM_IMG_SIZE_Y,DIM_BATCH_SIZE*DIM_PIXEL_SIZE};
        int[] outputDims = {1, mLabelList.size()};
        try {
            mDataOptions =
                    new FirebaseModelInputOutputOptions.Builder()
                            .setInputFormat(0, FirebaseModelDataType.FLOAT32, inputDims)
                            .setOutputFormat(0, FirebaseModelDataType.FLOAT32, outputDims)
                            .build();
            FirebaseLocalModelSource localSource =
                    new FirebaseLocalModelSource.Builder("asset")
                            .setAssetFilePath(LOCAL_MODEL_ASSET).build();
            FirebaseModelManager manager = FirebaseModelManager.getInstance();
            manager.registerLocalModelSource(localSource);
            FirebaseModelOptions modelOptions =
                    new FirebaseModelOptions.Builder()
                            .setLocalModelName("asset")
                            .build();
            mInterpreter = FirebaseModelInterpreter.getInstance(modelOptions);
        } catch (FirebaseMLException e) {
            showToast("Error while setting up the model");
            e.printStackTrace();
        }
    }

    private void runModelInference() {
        if (mInterpreter == null) {
            Log.e(TAG, "Image classifier has not been initialized; Skipped.");
            return;
        }
        // Create input data.
        Log.e(TAG, "running");

        ByteBuffer imgData = convertBitmapToByteBuffer(mBitmaps);

        try {
            FirebaseModelInputs inputs = new FirebaseModelInputs.Builder().add(imgData).build();
            // Here's where the magic happens!!
            mInterpreter
                    .run(inputs, mDataOptions)
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            e.printStackTrace();
                            showToast("Error running model inference");
                        }
                    })
                    .continueWith(
                            new Continuation<FirebaseModelOutputs, List<String>>() {
                                @Override
                                public List<String> then(Task<FirebaseModelOutputs> task) {
                                    float[][] labelProbArray = task.getResult()
                                            .<float[][]>getOutput(0);
                                    List<String> topLabels = getTopLabels(labelProbArray);
                                    return topLabels;
                                }
                            });
        } catch (FirebaseMLException e) {
            e.printStackTrace();
            showToast("Error running model inference");
        }

    }



    /**
     * Gets the top labels in the results.
     */
    private synchronized List<String> getTopLabels(float[][] labelProbArray) {
        for (int i = 0; i < mLabelList.size(); ++i) {
            sortedLabels.add(
                    new AbstractMap.SimpleEntry<>(mLabelList.get(i), labelProbArray[0][i] ));
            Log.i(TAG,mLabelList.get(i)+labelProbArray[0][i]);

            if (sortedLabels.size() > RESULTS_TO_SHOW) {
                sortedLabels.poll();
            }
        }
        List<String> result = new ArrayList<>();
        final int size = sortedLabels.size();
        for (int i = 0; i < size; ++i) {
            Map.Entry<String, Float> label = sortedLabels.poll();
            result.add(label.getKey() + ":" + label.getValue());
        }
        Log.e(TAG, "labels: " + result.toString());
        mText.append("\n"+result.get(RESULTS_TO_SHOW-1));
        mText.append("\n"+result.get(RESULTS_TO_SHOW-2));
        mText.append("\n"+result.get(RESULTS_TO_SHOW-3));
        return result;
    }

    /**
     * Reads label list from Assets.
     */
    private List<String> loadLabelList(Activity activity) {
        List<String> labelList = new ArrayList<>();
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(activity.getAssets().open
                             (LABEL_PATH)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                labelList.add(line);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read label list.", e);
        }
        return labelList;
    }

    /**
     * Writes Image data into a {@code ByteBuffer}.
     */
    private synchronized ByteBuffer convertBitmapToByteBuffer(Bitmap[] bitmaps) {

        ByteBuffer imgData =
                ByteBuffer.allocateDirect(
                        DIM_BATCH_SIZE * DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y * DIM_PIXEL_SIZE * 4);
        imgData.order(ByteOrder.nativeOrder());
        imgData.rewind();
        // Convert the image to float points.
        int pixel = 0;
        int b=0,i=0,j=0;
        try {
            for(b=0;b<DIM_BATCH_SIZE;b++){
                Bitmap scaledBitmap = bitmaps[b];
                scaledBitmap.getPixels(intValues[b],0, 224,0, 0,224,224);
            }
            for(i=0;i<DIM_IMG_SIZE_X;i++){
                for(j=0;j<DIM_IMG_SIZE_Y;j++){
                    for(b=0;b<DIM_BATCH_SIZE;b++){
                        final int val = intValues[b][pixel];
                        imgData.putFloat((float) ((((val >> 16) & 0xFF)) ));
                        imgData.putFloat((float) ((((val >> 8) & 0xFF)) ));
                        imgData.putFloat((float) ((((val) & 0xFF))));
                    }
                    pixel++;
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }

        imgData.rewind();
        return imgData;
    }


    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }


}
