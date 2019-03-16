package com.spring.common.model;

import com.spring.common.model.util.tools.RedisUtils;

public class SurvivalClamProcessor implements Runnable {

    private String value;
    private String key;
    private int lockTime;
    //线程关闭的标记
    private volatile Boolean signal;
    public SurvivalClamProcessor(String key, String value, int lockTime) {
        this.key = key;
        this.lockTime = lockTime;
        this.value = value;
        this.signal = Boolean.TRUE;
    }



    void stop() {
        this.signal = Boolean.FALSE;
    }
 
    @Override
    public void run() {
        int waitTime = lockTime * 1000 * 2 / 3;
        while (signal) {
            try {
                Thread.sleep(waitTime);
                if (RedisUtils.expandLockTime(key, value, lockTime) != 1) {
                    this.stop();
                }
            } catch (InterruptedException e) {
                RedisUtils.expandLockTime(key, value, lockTime);
            }
        }
    }
}
