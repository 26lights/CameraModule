/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Zillow
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is furnished
 * to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.yalantis.cameramodule.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import timber.log.Timber;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.AsyncTask;
import android.os.Environment;

import com.jni.bitmap_operations.JniBitmapHolder;
import com.yalantis.cameramodule.CameraConst;
import com.yalantis.cameramodule.interfaces.PhotoSavedListener;

public class SavingPhotoTask extends AsyncTask<Void, Void, File> {

    private byte[] data;
    private String name;
    private String path;
    private Integer maxSize;
    private int orientation;
    private PhotoSavedListener callback;
    private int compressQuality;

    public SavingPhotoTask(byte[] data, String name, String path, int orientation) {
        this(data, name, path, orientation, null, null, CameraConst.COMPRESS_QUALITY);
    }

    public SavingPhotoTask(byte[] data, String name, String path, int orientation, PhotoSavedListener callback) {
        this(data, name, path, orientation, null, callback, CameraConst.COMPRESS_QUALITY);
    }

    public SavingPhotoTask(byte[] data, String name, String path, int orientation, Integer maxSize, PhotoSavedListener callback, int compressQuality) {
        this.data = data;
        this.name = name;
        this.path = path;
        this.maxSize = maxSize;
        this.orientation = orientation;
        this.callback = callback;
        this.compressQuality = compressQuality;
    }

    @Override
    protected File doInBackground(Void... params) {
        File photo = getOutputMediaFile();
        if (photo == null) {
            Timber.e("Error creating media file, check storage permissions");
            return null;
        }

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(photo);
            saveByteArray(fos, data, orientation);
        } catch (FileNotFoundException e) {
            Timber.e(e, "File not found: " + e.getMessage());
        } catch (IOException e) {
            Timber.e(e, "File write failure: " + e.getMessage());
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException e) {
                Timber.e(e, e.getMessage());
            }
        }

        return photo;
    }

    private void saveByteArray(FileOutputStream fos, byte[] data, int orientation) {
        long totalTime = System.currentTimeMillis();
        long time = System.currentTimeMillis();

        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
        Timber.d("decodeByteArray: %1dms", System.currentTimeMillis() - time);

        time = System.currentTimeMillis();
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        try {
            // store the bitmap in the JNI "world"
            final JniBitmapHolder bitmapHolder = new JniBitmapHolder(bitmap);

            if (maxSize != null) {
                if (width > height && width > maxSize) {
                    float ratio = (float) height / width;
                    width = maxSize;
                    height = (int) (maxSize * ratio);
                    bitmapHolder.scaleBitmap(width, height, JniBitmapHolder.ScaleMethod.BilinearInterpolation);
                } else if (height > width && height > maxSize) {
                    float ratio = (float) width / height;
                    width = (int) (maxSize * ratio);
                    height = maxSize;
                    bitmapHolder.scaleBitmap(width, height, JniBitmapHolder.ScaleMethod.BilinearInterpolation);
                }
            }

            // no need for the bitmap on the java "world", since the operations are done on the JNI "world"
            bitmap.recycle();

            if (orientation != ExifInterface.ORIENTATION_UNDEFINED) {
                //rotate the bitmap:
                switch (Float.valueOf(orientation % 360).intValue()) {
                    case 90:
                    case -270:
                        bitmapHolder.rotateBitmapCw90();
                        break;
                    case 180:
                    case -180:
                        bitmapHolder.rotateBitmap180();
                        break;
                    case 270:
                    case -90:
                        bitmapHolder.rotateBitmapCcw90();
                        break;
                }
            }

            //get the output java bitmap , and free the one on the JNI "world"
            bitmap = bitmapHolder.getBitmapAndFree();
        } catch (Exception e) {

            if (maxSize != null) {
                Bitmap oldBitmap = bitmap;
                if (width > height && width > maxSize) {
                    float ratio = (float) height / width;
                    width = maxSize;
                    height = (int) (maxSize * ratio);
                    bitmap = Bitmap.createScaledBitmap(oldBitmap, width, height, true);
                } else if (height > width && height > maxSize) {
                    float ratio = (float) width / height;
                    width = (int) (maxSize * ratio);
                    height = maxSize;
                    bitmap = Bitmap.createScaledBitmap(oldBitmap, width, height, true);
                }
                if(oldBitmap != bitmap) {
                    oldBitmap.recycle();
                }
            }

            Matrix matrix = new Matrix();
            matrix.postRotate(orientation);
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
        }

        Timber.d("createBitmap: %1dms", System.currentTimeMillis() - time);
        time = System.currentTimeMillis();
        bitmap.compress(Bitmap.CompressFormat.JPEG, compressQuality, fos);
        Timber.d("compress: %1dms", System.currentTimeMillis() - time);

        bitmap.recycle();

        Timber.d("saveByteArrayWithOrientation: %1dms", System.currentTimeMillis() - totalTime);
    }

    @Override
    protected void onPostExecute(File file) {
        super.onPostExecute(file);
        photoSaved(file);
    }

    private void photoSaved(File photo) {
        if (photo != null) {
            if (callback != null) {
                callback.photoSaved(photo.getPath(), photo.getName());
            }
        }
    }

    /**
     * Create a File for saving an image
     */
    private File getOutputMediaFile() {
        // To be safe, we should check that the SDCard is mounted
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            Timber.e("External storage " + Environment.getExternalStorageState());
            return null;
        }

        File dir = new File(path);
        // Create the storage directory if it doesn't exist
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                Timber.e("Failed to create directory");
                return null;
            }
        }

        return new File(dir.getPath() + File.separator + name);
    }

}
