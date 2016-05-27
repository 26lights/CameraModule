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
import android.os.AsyncTask;

import com.jni.bitmap_operations.JniBitmapHolder;
import com.yalantis.cameramodule.CameraConst;
import com.yalantis.cameramodule.interfaces.PhotoSavedListener;

public class RotatePhotoTask extends AsyncTask<Void, Void, Void> {

    private String path;
    private float angle;
    private PhotoSavedListener callback;

    public RotatePhotoTask(String path, float angle, PhotoSavedListener callback) {
        this.path = path;
        this.angle = angle;
        this.callback = callback;
    }

    @Override
    protected Void doInBackground(Void... params) {
        Bitmap bitmap = BitmapFactory.decodeFile(path); // todo NPE

        // store the bitmap in the JNI "world"
        final JniBitmapHolder bitmapHolder=new JniBitmapHolder(bitmap);
        // no need for the bitmap on the java "world", since the operations are done on the JNI "world"
        bitmap.recycle();

        //rotate the bitmap:
        switch (Float.valueOf(angle%360).intValue()) {
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

        //get the output java bitmap , and free the one on the JNI "world"
        bitmap=bitmapHolder.getBitmapAndFree();

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(new File(path));

            bitmap.compress(Bitmap.CompressFormat.JPEG, CameraConst.COMPRESS_QUALITY, fos);

        } catch (FileNotFoundException e) {
            Timber.e(e, "File not found: " + e.getMessage());
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException e) {
                Timber.e(e, e.getMessage());
            }
        }
        bitmap.recycle();

        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        super.onPostExecute(aVoid);
        if (callback != null) {
            callback.photoSaved(path, null);
        }
    }
}
