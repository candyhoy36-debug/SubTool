package com.joy.subtool;

import android.app.Application;

import com.joy.subtool.data.AppDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SubToolApp extends Application {

    private static SubToolApp instance;
    private AppDatabase database;
    private ExecutorService dbExecutor;

    public static SubToolApp get() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    public synchronized AppDatabase getDatabase() {
        if (database == null) {
            database = AppDatabase.create(this);
        }
        return database;
    }

    public synchronized ExecutorService getDbExecutor() {
        if (dbExecutor == null) {
            dbExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "SubToolDb");
                t.setDaemon(true);
                return t;
            });
        }
        return dbExecutor;
    }
}
