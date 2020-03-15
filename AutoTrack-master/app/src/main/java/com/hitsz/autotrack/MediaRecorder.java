package com.hitsz.autotrack;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

import static android.support.constraint.Constraints.TAG;

/**
 * @author Lance
 * @date 2018/10/10
 */
public class MediaRecorder {
    /**
     * 保存视频的宽、高与地址
     */
    private int mWidth;
    private int mHeight;
    private String mPath;
    /**
     * 编码器与复用器
     */
    private MediaCodec mMediaCodec;
    private MediaMuxer mMuxer;
    /**
     * 变量：控制参数
     */
    private boolean isStart;
    private int track;
    private float mSpeed;
    private long mStartTime;
    private Handler mHandler;

    /**
     * 5个级别的速度
     */
    public enum Speed {
        MODE_EXTRA_SLOW, MODE_SLOW, MODE_NORMAL, MODE_FAST, MODE_EXTRA_FAST
    }


    public void setOutputFile(String path) {
        mPath = path;
    }

    public void setVideoSize(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    public void setSpeed(Speed speed) {
        switch (speed) {
            case MODE_EXTRA_SLOW:
                mSpeed = 0.3f;
                break;
            case MODE_SLOW:
                mSpeed = 0.5f;
                break;
            case MODE_NORMAL:
                mSpeed = 1.f;
                break;
            case MODE_FAST:
                mSpeed = 2.f;
                break;
            case MODE_EXTRA_FAST:
                mSpeed = 3.f;
                break;
            default:
                mSpeed = 1.0f;
                break;
        }
    }

    public void prepare() throws IOException {

        /**
         * 1、创建视频复用器(输出mp4)
         */
        mMuxer = new MediaMuxer(mPath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        /**
         * 2、设置视频参数
         */
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,
                mWidth, mHeight);
        //编码器输入数据格式 camera回调为nv21
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities
                .COLOR_FormatYUV420Flexible);
        //码率
        format.setInteger(MediaFormat.KEY_BIT_RATE, 1500_000);
        //帧率
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 25);
        //关键帧间隔，直播为了秒开，需要设置更低
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10);

        /**
         * 3、根据参数创建编码器
         */
        mMediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        mMediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        /**
         * 4、准备控制变量与线程
         */
        isStart = true;
        //开始时间戳
        mStartTime = -1;
        //编码线程
        HandlerThread handlerThread = new HandlerThread("VideoCodec");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());
    }

    public void start() {
        mMediaCodec.start();
    }


    public void stop() {
        isStart = false;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mMediaCodec.stop();
                mMediaCodec.release();
                mMediaCodec = null;
                mMuxer.stop();
                mMuxer.release();
                mMuxer = null;
                mHandler.getLooper().quit();
            }
        });

    }


    public void addFrame(final byte[] data) {
        Log.d(TAG, "addFrame in");
        if (!isStart) {
            return;
        }
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                //当前图像的时间戳，单位：微妙
                long pts;
                if (mStartTime == -1) {
                    mStartTime = System.nanoTime();
                    pts = 0;
                } else {
                    pts = (System.nanoTime() - mStartTime) / 1000;
                }
                //获得编码器输入缓冲区
                int index = mMediaCodec.dequeueInputBuffer(2_000);
                if (index >= 0) {
                    //向输入缓冲区中 放入待编码数据
                    ByteBuffer inBuf = mMediaCodec.getInputBuffer(index);
                    inBuf.clear();
                    inBuf.put(data);
                    mMediaCodec.queueInputBuffer(index, 0, data.length, pts, 0);
                }
                //从编码器获取输出缓冲区数据(编码完成数据)
                codec();
            }
        });
    }

    private void codec() {
        Log.d(TAG, "codec in");
        while (true) {
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            //获得编码器输出缓冲区状态
            int encoderStatus = mMediaCodec.dequeueOutputBuffer(bufferInfo, 2_000);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                //需要更多输入数据
                break;
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                //输出格式发生改变  开始编码首先回调此结果
                //开启复用器，加入视频流
                MediaFormat newFormat = mMediaCodec.getOutputFormat();
                track = mMuxer.addTrack(newFormat);
                mMuxer.start();
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                //可以忽略
            } else {
                //获得输出数据(编码完成数据)
                ByteBuffer outBuf = mMediaCodec.getOutputBuffer(encoderStatus);
                //编码的配置信息，不是编码图像数据 不用写出去
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    continue;
                }
                if (bufferInfo.size != 0) {
                    //控制时间戳
                    bufferInfo.presentationTimeUs = (long) (bufferInfo.presentationTimeUs /
                            mSpeed);
                    outBuf.position(bufferInfo.offset);
                    //设置读写总长度 编码后的数据大小不一定，根据此次编码写入
                    //比如本身大小为100,实际本次只占用50(bufferInfo.size),
                    //则设置此buf只能读50
                    outBuf.limit(bufferInfo.offset + bufferInfo.size);
                    mMuxer.writeSampleData(track, outBuf, bufferInfo);
                }
                mMediaCodec.releaseOutputBuffer(encoderStatus, false);
            }
        }
    }


}
